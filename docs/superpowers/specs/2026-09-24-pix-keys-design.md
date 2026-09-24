# PIX keys: registry, lookup and transfers by key

Date: 2026-09-24 · Status: approved design, pending implementation plan

## Goal

End users send money by **PIX key** (CPF, e-mail or random key) and never type an account UUID. Before
confirming, they see who will receive the money (full name and masked CPF), as in Brazilian banking apps.

Success criteria:

- A user registers PIX keys on their own accounts and can list and remove them.
- A transfer is created from a key lookup; the money goes to exactly the account whose recipient the user saw.
- The simulated deposit (`POST /accounts/deposit`) resolves keys through the same registry.
- No client-facing screen or request needs a destination account UUID.
- The full flow (register key → look up → confirm → `APPROVED`) works from the frontend.

## Decisions

| Topic | Decision |
|---|---|
| Key types | `CPF`, `EMAIL`, `RANDOM`. Phone is out of scope (auth-service stores no phone and would need SMS verification). |
| Key onboarding | Always explicit. Nothing is registered automatically; an unregistered key is not found (404), including on deposit. |
| Transfer types | Every type (`PIX`, `CREDIT`, `DEBIT`) addresses the recipient by key lookup. `type` remains a transaction attribute used by fraud scoring. |
| Lookup-to-payment binding | Short-lived `lookupId` (approach 2), the equivalent of the DICT lookup reference carried by a real PIX payment. |
| Where keys live | A dedicated `pix` package inside account-service. Keys reference accounts by foreign key and the per-account limit needs a lock on the account row, so a separate service would add distributed consistency without benefit. The package boundary keeps a later extraction possible. |
| Schema management | Hibernate `ddl-auto: update`, like the rest of the project. Migrating every service to Flyway is a separate follow-up. |

## Data model (account-service)

Table `tb_pix_keys`:

| Column | Type | Rule |
|---|---|---|
| `id` | UUID | primary key |
| `key_type` | `CPF` \| `EMAIL` \| `RANDOM` | not null |
| `key_value` | varchar, normalized | not null, **unique** |
| `account_id` | UUID | not null, FK → `tb_accounts.id` |
| `owner_id` | UUID | not null (account owner; simplifies authorization and queries) |
| `created_at` | timestamp | not null |

Rules:

- **Global uniqueness**: a key value belongs to at most one account in the system, enforced by the unique
  constraint on the normalized value. `Ana@Mail.com` and `ana@mail.com` are the same key.
- **Ownership**: CPF and e-mail keys can only be the registering user's own CPF/e-mail. The client sends only
  the key type; account-service reads the value from auth-service for the authenticated user, so registering
  someone else's CPF or e-mail is impossible rather than merely checked.
- **Random keys** are generated server-side (`UUID.randomUUID()`); the client never chooses the value.
- At most **5 keys per account** (the PIX limit for individuals).
- Keys can only be registered on the caller's own account with `status = ACTIVE`.
- Removing a key deletes the row; the value becomes available again. Transaction history stays in the ledger.

Normalization (shared by registration, lookup and deposit):

- E-mail: trimmed and lower-cased (same rule as auth-service `Identifiers.email`).
- CPF: digits only, 11 digits (same rule as auth-service `Identifiers.cpf`).
- Random: canonical lower-case UUID.
- Lookup input type is detected by format: contains `@` → e-mail; 11 digits after removing punctuation → CPF;
  UUID format → random; anything else → 400.

## API

### account-service, public (through the gateway)

| Method and path | Body | Success | Errors |
|---|---|---|---|
| `POST /accounts/{accountId}/pix-keys` | `{ "type": "CPF" \| "EMAIL" \| "RANDOM" }` | `201 { id, type, value, accountId, createdAt }` | 403 not the owner, 404 unknown account, 409 key already registered, 422 limit reached or account inactive |
| `GET /accounts/{accountId}/pix-keys` | — | `200 [ { id, type, value, accountId, createdAt } ]` | 403, 404 |
| `DELETE /accounts/{accountId}/pix-keys/{keyId}` | — | `204` | 403, 404 |
| `POST /accounts/pix-keys/lookup` | `{ "key": "ana@mail.com" }` | `200 { lookupId, recipientName, maskedCpf, keyType, expiresAt }` | 400 invalid format, 404 key not found, 429 rate limited (`Retry-After`), 503 dependency down |

- The key travels in the request body, never in the URL (URLs end up in logs, gateway access logs and traces).
- `recipientName` is the full name; `maskedCpf` looks like `***.456.789-**`.

### account-service, internal (not routed by the gateway)

`GET /internal/pix-keys/lookups/{lookupId}` → `200 { destinationAccountId }`, or `404` when the lookup does not
exist, has expired or was made by another user (always the same response, to reveal nothing). Called by
transaction-service forwarding the end user's bearer token. The gateway only routes `/accounts/**`, so this
path is unreachable from clients; a gateway test guards that.

### transaction-service

`POST /transactions` replaces `destinationAccountId` with `lookupId`:

```json
{ "sourceAccountId": "uuid", "lookupId": "uuid", "amount": 50.00, "type": "PIX",
  "deviceId": "…", "ipAddress": null, "idempotencyKey": "uuid" }
```

New errors: `422` lookup expired or invalid; `422` destination equals source. Responses and history keep
`destinationAccountId` unchanged.

### auth-service

`GET /auth/users/lookup` (service token only) accepts `?id=` in addition to `email` and `cpf`.

### Deposit

`POST /accounts/deposit` keeps its contract (`{ pixKey, amount }`) but resolves the key through the registry;
an unregistered key returns 404. It no longer calls auth-service.

## Flows

### Register a key

1. Load the account: 404 if missing, 403 if not the caller's, 422 if not `ACTIVE`.
2. Resolve the value: CPF/e-mail from auth-service (`lookup?id=` with a service token), called **outside** any
   DB transaction; random generated locally. Normalize.
3. In one short transaction: lock the account row (`PESSIMISTIC_WRITE`), count its keys (422 at 5), insert.
   The lock serializes concurrent registrations on one account; the unique constraint rejects a duplicate
   value across users, mapped to `PixKeyAlreadyRegisteredException` → 409.

### Look up a key

1. Rate limit first: Redis fixed window, 20 lookups per user per 60 s; over the limit → 429 with `Retry-After`.
   Not-found lookups count too, which is what blocks CPF enumeration.
2. Detect type, normalize, find the key: 404 if absent.
3. Fetch the owner's name and CPF from auth-service (`lookup?id=`) and mask the CPF.
4. Store `pix:lookup:{lookupId}` → `{ accountId, requesterId }` in Redis with a 300 s TTL; return.

The `lookupId` is a random UUID, valid until it expires and **not consumed** on use: if a transfer fails (for
example, insufficient funds) the user can retry without a new lookup; idempotency keys still prevent double
submission. Redis stores only IDs, never the CPF or e-mail.

### Resolve a lookup (internal)

Read `pix:lookup:{lookupId}`; if absent, expired or `requesterId` differs from the token subject → 404;
otherwise return `destinationAccountId`.

### Create a transaction

Validation order: idempotency key used → 409; insufficient available balance → 422; resolve `lookupId`
(replaces today's `accountExists` check; 404 → `InvalidPixLookupException` → 422); destination equals source →
`SameAccountTransferException` → 422; save; publish after commit (unchanged from PR #6). Transfers between two
different accounts of the same user remain allowed.

### Deposit

Normalize `pixKey`, find it in the registry (404 if absent), credit the account.

### Unchanged

Kafka events, fraud-detection, ledger and notification: `transaction.created` still carries
`destinationAccountId`.

## Errors

All use the existing `ErrorResponse`; backend messages stay in English as today and are listed in
`docs/API.md`.

| Exception | Status | Message |
|---|---|---|
| `PixKeyAlreadyRegisteredException` | 409 | This PIX key is already registered |
| `PixKeyLimitReachedException` | 422 | An account can have at most 5 PIX keys |
| `InactiveAccountException` | 422 | Account is not active |
| `PixKeyNotFoundException` | 404 | PIX key not found |
| `InvalidPixKeyFormatException` | 400 | Invalid PIX key format |
| `PixLookupRateLimitedException` | 429 | Too many PIX key lookups, try again later |
| `InvalidPixLookupException` (transaction) | 422 | PIX key lookup expired or invalid, look up the key again |
| `SameAccountTransferException` (transaction) | 422 | Cannot transfer to the same account |

## Security and privacy

- CPF and e-mail never appear in clear in URLs, logs, span attributes or Redis. Logs mask keys
  (`***.456.789-**`, `a***@mail.com`).
- Every route requires a JWT; key routes check account ownership; the internal route checks the requester.
- Enumeration resistance: not-found lookups count toward the rate limit; lookup resolution returns a uniform 404.
- Redis unavailable → lookup **fails closed** with 503 (failing open would allow enumeration).
- auth-service unavailable → CPF/e-mail registration and lookup return 503 (existing handling).
- Known risk, out of scope: auth-service does not verify e-mail ownership at sign-up, so an e-mail key proves
  the account holder registered that e-mail, not that they control the mailbox.

## Observability

Prometheus counters: `pix_key_registrations_total{type}`,
`pix_key_lookups_total{result="found|not_found|rate_limited"}`, `pix_lookup_resolutions_total{result}`.
HTTP and Redis spans are already instrumented by OpenTelemetry; no span attribute carries a key value.

## Testing (TDD)

- **account-service**
  - Unit: key parsing/normalization/format detection; CPF and e-mail masking; `PixKeyService` rules (limit,
    ownership, inactive account, value sourced from auth-service); rate limiter (21st lookup blocked,
    `Retry-After`).
  - Integration (Testcontainers Postgres + Redis): the same value registered concurrently by two users → one
    201, one 409; six concurrent registrations on one account → exactly 5 stored; lookup → resolve round trip,
    other user → 404, expired → 404 (short TTL in test); deposit by registered and unregistered key.
  - Web security: 401 without token, 403 on another user's account.
- **transaction-service**: creation with valid, expired (422) and same-account (422) lookups, validation order;
  `AccountServiceClient` maps the internal 404 to `InvalidPixLookupException`.
- **auth-service**: `lookup?id=` works with a service token only.
- **api-gateway**: `/internal/**` is not routed.

All of it runs in CI.

## Delivery

1. Branch `feat/pix-keys`, one backend PR with commits per service: auth-service → account-service →
   transaction-service → api-gateway test → docs.
2. `docs/API.md` is updated first in the implementation, since it is the contract the frontend follows.
3. Frontend (Codex, worktree `feat/frontend`): "My PIX keys" screen and a two-step transfer (look up → confirm).
   The current frontend sends `destinationAccountId` and will break against this backend; acceptable because
   it is not on `main` yet, but it must be updated before the end-to-end test.
4. Final validation: Playwright end-to-end test of register → look up → confirm → `APPROVED`.

## Out of scope

Phone keys; key portability and claims; counterpart names in statements; Flyway migration (next PR).
