# FraudShield — HTTP API (through api-gateway)

Every client call goes to **api-gateway** at `http://localhost:8080`. The gateway routes
`/auth/**`, `/accounts/**`, `/transactions/**` and `/ledger/**` to the services.

- JSON in and out. Money is a JSON number with up to 2 decimals (`150.00`).
- IDs are UUID strings. Timestamps are ISO-8601 strings.
- Authenticated routes need `Authorization: Bearer <accessToken>`.
- Interactive docs: `http://localhost:8080/swagger-ui.html` (generated from the code, local runs only). This file stays the hand-written contract for the frontend.

---

## Errors

Every error uses the same body:

```json
{
  "timestamp": "2026-09-23T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/auth/register",
  "fieldErrors": { "email": "Email must be a valid format" }
}
```

`fieldErrors` is present only on validation errors (400). Show `fieldErrors` next to the
matching form fields and `message` otherwise.

| Status | Meaning for a client |
|---|---|
| 400 | Validation failed or malformed body |
| 401 | Missing, invalid or expired access token — or wrong credentials on login |
| 403 | Authenticated, but not allowed (e.g. someone else's account) |
| 404 | Not found (also used to hide transactions you cannot see) |
| 409 | Conflict (e-mail/CPF already registered, idempotency key reused, PIX key already registered) |
| 422 | Business rule: insufficient funds, expired PIX lookup, same account, key limit, inactive account |
| 429 | Too many requests: failed logins for this e-mail (locked 15 min) or PIX key lookups (see `Retry-After`) |
| 503 | A downstream service is unavailable |

---

## Auth

### `POST /auth/register` — public
```json
{
  "fullName": "Ana Souza",
  "email": "ana@example.com",
  "cpf": "52998224725",
  "password": "Senha@123",
  "birthDate": "1990-05-20"
}
```
- `cpf`: 11 digits with valid check digits; punctuation (`529.982.247-25`) is accepted and stripped.
- E-mail is stored trimmed and lower-cased, so login and duplicate checks ignore case.
- `password`: at least 8 characters with an uppercase letter, a lowercase letter, a number and a special character.
- `birthDate`: `YYYY-MM-DD`, at least 18 years old.

`201`:
```json
{ "id": "uuid", "email": "ana@example.com", "fullname": "Ana Souza", "birthDate": "1990-05-20", "createdAt": "2026-09-23T09:00:00" }
```
Errors: `400` (fieldErrors), `409` e-mail or CPF already registered.

### `POST /auth/login` — public
```json
{ "email": "ana@example.com", "password": "Senha@123" }
```
`200`:
```json
{ "accessToken": "jwt", "refreshToken": "opaque", "tokenType": "Bearer", "expiresIn": 900 }
```
`expiresIn` is in seconds. Errors: `401` wrong credentials, `429` locked after 5 failures.

The access token is a JWT; its payload has `sub` (user id), `email`, `fullName` and `exp`.
Decoding it on the client is fine for display; the server validates it on every call.

### `POST /auth/refresh` — public
```json
{ "refreshToken": "opaque" }
```
`200`: same body as login, **with a new refresh token** (the old one stops working).
`401` if the refresh token is invalid, expired or already used — send the user to login.

Reusing an old refresh token revokes all of the user's sessions, so a client must always
store the newest refresh token and never retry with the previous one.

### `POST /auth/logout` — public
```json
{ "refreshToken": "opaque" }
```
`204`. Discard both tokens on the client.

### `PUT /auth/password` — authenticated
```json
{ "currentPassword": "Senha@123", "newPassword": "NovaSenha#456", "confirmPassword": "NovaSenha#456" }
```
`204`. All refresh tokens are revoked: the client should log in again.
Errors: `400` (fieldErrors or passwords do not match), `401` wrong current password.

---

## Accounts

### `GET /accounts` — authenticated
The caller's accounts, oldest first. Empty array if the user has none yet.

`200`:
```json
[
  {
    "id": "uuid",
    "ownerId": "uuid",
    "ownerName": "Ana Souza",
    "balance": 1000.00,
    "lockedBalance": 0.00,
    "status": "ACTIVE",
    "createdAt": "2026-09-23T09:00:00"
  }
]
```

### `POST /accounts` — authenticated
```json
{ "ownerId": "<sub from the access token>", "ownerName": "Ana Souza" }
```
`201`: the account (same shape as above, balance `0`). `403` if `ownerId` is not the caller.

### `GET /accounts/{id}/balance` — authenticated, owner only
`200`:
```json
{ "name": "Ana Souza", "accountId": "uuid", "balance": 1000.00, "lockedBalance": 100.00, "availableBalance": 900.00 }
```
`lockedBalance` is money reserved by transfers still being analyzed. `403` not yours, `404` unknown.

### `POST /accounts/deposit` — public (simulated incoming PIX)
```json
{ "pixKey": "ana@example.com", "amount": 150.00 }
```
`pixKey` must be a **registered** PIX key: e-mail (any case), CPF (with or without punctuation) or random key.
`200`:
```json
{ "receiverName": "Ana Souza", "amount": 150.00 }
```
Errors: `400` validation or invalid key format (message: "Invalid PIX key format. Use a CPF (11 digits), an e-mail or a random key (UUID)."), `404` key not registered.
Note: this simulated endpoint is public and not rate limited; it confirms whether a key is registered. It is
meant for local testing, not production.

## PIX keys

A PIX key points to one account. Values are unique across all users. An account holds at most 5 keys.
CPF and e-mail keys can only be the caller's own CPF/e-mail: the client sends the type, the server fills in
the value.

### `POST /accounts/{accountId}/pix-keys` — authenticated, owner only
```json
{ "type": "CPF" }
```
`type`: `CPF`, `EMAIL` or `RANDOM` (random keys are generated by the server). `201`:
```json
{ "id": "uuid", "type": "CPF", "value": "52998224725", "accountId": "uuid", "createdAt": "2026-09-24T10:00:00" }
```
Errors: `400` unknown type, `403` not your account, `404` unknown account, `409` key already registered,
`422` 5 keys already registered or account not active, `503` auth-service unavailable.

### `GET /accounts/{accountId}/pix-keys` — authenticated, owner only
`200`: array of keys (same shape as above), oldest first. Errors: `403`, `404`.

### `DELETE /accounts/{accountId}/pix-keys/{keyId}` — authenticated, owner only
`204`. Errors: `403`, `404` unknown account or key.

### `POST /accounts/pix-keys/lookup` — authenticated
Look up the recipient **before** a transfer. The key goes in the body, never in the URL.
```json
{ "key": "ana@example.com" }
```
`200`:
```json
{
  "lookupId": "uuid",
  "recipientName": "Ana Souza",
  "maskedCpf": "***.982.247-**",
  "keyType": "EMAIL",
  "expiresAt": "2026-09-24T10:05:00Z"
}
```
Show `recipientName` and `maskedCpf` for confirmation and send `lookupId` in `POST /transactions`.
A `lookupId` is valid for 5 minutes, only for the user who made the lookup, and can be reused while valid
(e.g. to retry after a failed transfer).
Errors: `400` invalid key format (message: "Invalid PIX key format. Use a CPF (11 digits), an e-mail or a random key (UUID)."), `404` key not registered, `429` more than 20 lookups per minute
(`Retry-After` header, in seconds), `503` dependency unavailable.

---

## Transactions

### `POST /transactions` — authenticated
```json
{
  "sourceAccountId": "uuid",
  "lookupId": "uuid from POST /accounts/pix-keys/lookup",
  "amount": 100.00,
  "type": "PIX",
  "deviceId": "browser-generated id, stable per browser",
  "ipAddress": null,
  "idempotencyKey": "new UUID per transfer attempt"
}
```
- `lookupId`: from the PIX key lookup the user confirmed. Every `type` addresses the recipient this way.
- `type`: `PIX`, `CREDIT` or `DEBIT`.
- `deviceId` and `ipAddress` are optional fraud signals. A browser client should generate a
  random `deviceId` once and keep it in `localStorage`; send `ipAddress` as `null`.
- `idempotencyKey`: generate a new one for each transfer the user submits and reuse it only
  when retrying **the same** submission (e.g. after a network error).

`201`:
```json
{
  "id": "uuid",
  "sourceAccountId": "uuid",
  "destinationAccountId": "uuid",
  "amount": 100.00,
  "type": "PIX",
  "status": "CREATED",
  "createdAt": "2026-09-23T09:05:00"
}
```
Errors: `400`, `403` source account is not yours, `404` unknown source account,
`409` idempotency key already used, `422` insufficient funds, PIX lookup expired or invalid (look the key up
again) or destination is the source account.

**Status lifecycle.** A transfer is created as `CREATED` and is analyzed asynchronously,
usually within a few seconds. Its status then becomes one of:

| Status | Meaning | Money |
|---|---|---|
| `APPROVED` | Passed fraud analysis | Moved from source to destination |
| `DENIED` | Rejected as likely fraud | Not moved; reservation released |
| `FLAGGED` | Held for manual review | Not moved; stays reserved (review is not implemented yet) |

Poll `GET /transactions/{id}` (e.g. every 1–2 s, up to ~30 s) to show the outcome.

### `GET /transactions/{id}` — authenticated
`200`: same body as creation, with the current `status`. Visible to the owners of the source
and of the destination account; anyone else gets `404`.

### `GET /transactions?accountId={id}&page=0&size=20` — authenticated, owner only
Transactions where the account is the source **or** the destination, newest first.
`page` starts at 0; `size` is clamped to 1..100 (default 20).

`200`:
```json
{
  "transactions": [ { "...": "same shape as GET /transactions/{id}" } ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```
Errors: `400` missing `accountId`, `403` not your account, `404` unknown account.

---

## Ledger

### `GET /ledger/account/{id}?page=0&size=20` — authenticated, owner only
The audit trail of every transfer event involving the account, newest first.

`200`:
```json
{
  "entries": [
    {
      "id": "uuid",
      "transactionId": "uuid",
      "eventType": "TRANSACTION_APPROVED",
      "eventPayload": { "amount": 100.0, "fraudScore": 0.02, "modelVersion": "v1.0.0", "...": "..." },
      "kafkaTopic": "transaction.approved",
      "kafkaOffset": 12,
      "recordedAt": "2026-09-23T12:05:01Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 2, "totalPages": 1
}
```
`eventType`: `TRANSACTION_CREATED`, `TRANSACTION_APPROVED`, `TRANSACTION_DENIED`,
`TRANSACTION_FLAGGED`. Outcome events carry `fraudScore` (0–1) and, for denied and flagged,
a `reason`. Deposits are not recorded in the ledger.
