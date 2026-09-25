# FraudShield — HTTP API guide

The endpoint reference (routes, request and response bodies, field rules and status codes) is the
**Swagger UI** at `http://localhost:8080/swagger-ui.html`, generated from the code. Pick a service in the
top-right selector; each spec is also served as JSON at `/api-docs/{auth,account,transaction,ledger}`.
To try a call, log in with `POST /auth/login` and paste the `accessToken` into **Authorize**.

This guide covers only what a client needs beyond the reference: conventions, sessions and the
transfer flow.

## Conventions

- Every call goes to **api-gateway** at `http://localhost:8080`, which routes `/auth/**`, `/accounts/**`,
  `/transactions/**` and `/ledger/**`.
- JSON in and out. Money is a JSON number with up to 2 decimals (`150.00`). IDs are UUID strings and
  timestamps are ISO-8601 strings.
- Authenticated routes need `Authorization: Bearer <accessToken>`.

## Errors

Every error has the same body (`ErrorResponse` in Swagger):

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

`fieldErrors` comes only with validation errors (400): show each one next to its form field, and `message`
otherwise. `404` is also used to hide resources the caller may not see, and `429` from the PIX key lookup
carries a `Retry-After` header in seconds.

## Sessions

- The access token lasts `expiresIn` seconds (15 minutes). Its JWT payload has `sub` (user id), `email`,
  `fullName` and `exp`; decoding it for display is fine, the server validates it on every call.
- `POST /auth/refresh` returns a **new** refresh token and invalidates the old one. Always store the newest
  one: reusing an old refresh token revokes all of the user's sessions. A `401` from refresh means log in again.
- After `PUT /auth/password` every session is revoked, so the client logs in again.
- Five failed logins for an e-mail lock it for 15 minutes (`429`).

## Sending money

1. **PIX keys.** A user registers keys on their accounts (`POST /accounts/{accountId}/pix-keys`). For CPF and
   e-mail keys the client sends only the type; the server fills in the user's own value.
2. **Look up the recipient.** `POST /accounts/pix-keys/lookup` with the key in the body, never in the URL.
   Show `recipientName` and `maskedCpf` so the user confirms who receives the money.
3. **Create the transfer.** `POST /transactions` with the `lookupId` from step 2. A `lookupId` lasts 5 minutes,
   works only for the user who made the lookup and can be reused while valid, e.g. to retry.
   - `idempotencyKey`: a new UUID per transfer the user submits, reused only to retry that same submission.
   - `deviceId`: a random id the browser generates once and keeps in `localStorage`. Send `ipAddress` as `null`.
   - A `422` for an expired or invalid lookup means going back to step 2.
4. **Wait for the outcome.** The transfer is created as `CREATED` and analyzed asynchronously, usually within a
   few seconds. Poll `GET /transactions/{id}` every 1–2 s, for up to ~30 s:

| Status | Meaning | Money |
|---|---|---|
| `APPROVED` | Passed fraud analysis | Moved from source to destination |
| `DENIED` | Rejected as likely fraud | Not moved; reservation released |
| `FLAGGED` | Held for manual review | Not moved; stays reserved (review is not implemented yet) |

While a transfer is being analyzed, its amount shows up in the balance as `lockedBalance`.

`POST /accounts/deposit` simulates an incoming PIX from another bank. It is public, not rate limited and
reveals whether a key is registered, so it exists for local testing only.
