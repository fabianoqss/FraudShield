# Security

This document describes how FraudShield authenticates and authorizes requests, which vulnerabilities were found and fixed, and which risks remain open by design or because they belong to the infrastructure phase.

FraudShield is a portfolio project: every account, payment and bank integration is simulated. The remaining risks below are acceptable for a local demo, not for a real deployment.

---

## 1. Security model

### Authentication — RS256 + JWKS

- `auth-service` signs access tokens with **RS256**. The private key (`JWT_PRIVATE_KEY`) exists only in `auth-service`.
- The public key is published at `GET /.well-known/jwks.json` (`auth-service`, port 8081).
- `api-gateway`, `account-service`, `transaction-service` and `ledger-service` are **OAuth2 Resource Servers**: each one fetches the JWKS (`AUTH_JWKS_URI`) and validates signature, expiry and issuer (`iss=fraudshield-auth-service`) on every request.
- No service trusts identity headers such as `X-User-Id`. A request that reaches a service port directly still needs a valid token.

Why RS256 instead of HS256: with a shared HMAC secret, every service that can *verify* a token can also *forge* one. With RS256, a compromised service only holds the public key.

### Token types

Every token carries a `token_type` claim.

| Type | Issued by | `sub` | Grants | Used for |
|---|---|---|---|---|
| `user` | `POST /auth/login`, `POST /auth/refresh` | user UUID | `ROLE_USER` | All user routes |
| `service` | `POST /auth/service-token` (client credentials) | client id | `SCOPE_<scope>` | `account-service` → `GET /auth/users/lookup` (`users:lookup`) |

A service token on a user route gets `403`. A user token on `/auth/users/lookup` gets `403`.

### Authorization

- Users only access their own accounts, balances and ledgers.
- A transaction is visible only to the owner of its source or destination account. Anyone else gets `404`, so the transaction's existence is not revealed.
- `api-gateway` blocks `/auth/service-token` and `/auth/users/lookup` from the outside.
- `transaction-service` and `ledger-service` forward the caller's own token when they check ownership with `account-service`.

### Funds integrity

- A transfer is rejected up front (`422`) if the amount exceeds the source's available balance or if the destination account does not exist.
- `account-service` reserves funds with a conditional update (`balance - lockedBalance >= amount`).
- Each transaction is settled at most once when its events are processed one after another. Balance locks are kept and marked `settled`, so redelivered approvals or denials are ignored, and a lock that arrives after its approval is not applied. Concurrent processing of the same transaction is not yet protected (see 3.7).
- Settlement never debits more than was reserved or is still available, and never debits when the destination is missing.
- Kafka consumers keep their idempotency keys in Redis under their own namespace (`processed:{service}:{eventId}`). Only `account-service` backs this with a database flag; the ledger relies on Redis alone (see 3.14).

### Other protections

- **Brute force:** after 5 failed logins for the same e-mail within 15 minutes, `/auth/login` returns `429` (`login.max-failures`, `login.lock-duration`).
- **Refresh tokens:** rotated on every use. Reusing an already-rotated refresh token revokes all of the user's active sessions. Changing the password revokes all refresh tokens.
- **Public deposit:** the response contains only the receiver's name and the amount, with no e-mail, CPF, account id or balance.
- **Pagination:** ledger pages are clamped to `1 <= size <= 100`.
- **Local infrastructure:** every port published by `docker-compose` is bound to `127.0.0.1`.
- **Containers** run as a non-root user.

### Public routes

`/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/accounts/deposit`, `/.well-known/jwks.json` (auth-service only), `/actuator/health`, `/actuator/prometheus`.

---

## 2. Fixed vulnerabilities

| Severity | Issue | Fix |
|---|---|---|
| Critical | Services authenticated users from the `X-User-Id` header. With service ports exposed, anyone could impersonate any user by sending that header. | Per-service JWT validation (Resource Server, RS256 + JWKS). The header is ignored. |
| Critical | No balance check anywhere: a zero-balance account could transfer any amount, and approval drove the balance negative and credited money that never existed. | Up-front `422` plus conditional reservation and settlement in `account-service`. |
| Critical | A redelivered `transaction.approved` debited the source again ("no lock found, debiting directly"). | Locks are marked `settled`, and settlement happens at most once. |
| High | Shared Redis idempotency keys: the first consumer of an event made the others skip it, so `fraud-detection-service` never analyzed transactions. | Keys namespaced per service. |
| High | A transfer to a non-existent account debited the source and credited nothing. | Destination validated at creation and again at settlement. |
| High | `GET /transactions/{id}` returned any transaction to any authenticated user (IDOR). | Only the source or destination owner can read it; everyone else gets `404`. |
| High | The public `POST /accounts/deposit` echoed the receiver's e-mail, CPF, account id and balance, which allowed PII and balance enumeration from a CPF or e-mail. | The response carries only receiver name and amount. |
| High | `/auth/users/lookup` (e-mail and CPF lookup) was public on `auth-service`. | Requires a service token with the `users:lookup` scope and is blocked at the gateway. |
| High | Kafka (no auth), Redis (no password) and the databases were published on `0.0.0.0`. | Bound to `127.0.0.1`. |
| High | No brute-force protection on login. | Per-e-mail throttling (`429`). |
| Medium | A service token on a user route crashed on `UUID.fromString(sub)`. | `token_type` claim, and user routes require `ROLE_USER`. |
| Medium | Unbounded ledger page size. | Clamped to 100. |
| Low | `isForeignIp` treated the whole `172.0.0.0/8` block as private. | Only the real private ranges (`172.16.0.0/12` etc.) count. |

---

## 3. Known risks

### 3.1 Kafka and Redis without authentication — infrastructure

**What it is.** Kafka accepts messages from any client that can connect, and Redis has no password.

**Why it matters.** `account-service` trusts every message on `transaction.approved`. Anyone who can reach Kafka can publish a forged approval and move money between accounts. This was reproduced locally with `kafka-console-producer`. With Redis access, an attacker could write `processed:account-service:{eventId}` and make a legitimate event be skipped.

**Current mitigation.** All ports are bound to `127.0.0.1`, so only the host machine can reach them. This does not isolate them from other containers: anything running on the same Docker network reaches Kafka and Redis directly.

**Definitive fix (Phase 3 — Kubernetes).**
- Kafka with SASL (one credential per service) and ACLs: for example, only `fraud-detection-service` may **write** to `transaction.approved`, and `account-service` may only **read** from it.
- A Redis password (`requirepass`) or ACL users.
- `NetworkPolicy` so each service only talks to the components it needs.

### 3.2 Public PIX deposit — design (intentional)

**What it is.** `POST /accounts/deposit` needs no authentication and credits any amount to any PIX key (e-mail or CPF).

**Why it exists.** It simulates money arriving from *another bank*. Without it there is no way to fund accounts in the demo.

**Risk.** Anyone can create money. A real system has no such endpoint: incoming transfers arrive through the central bank's settlement network, with mutual authentication between institutions. The endpoint also confirms whether a PIX key exists and returns the receiver's name, which is how real PIX behaves.

**Options if the demo needs hardening.**
- Cap the amount per deposit.
- Require a service token, simulating a partner bank, as `/auth/users/lookup` does.
- Enable the endpoint only in a `dev` profile.

### 3.3 No compensation event when settlement fails — design (saga consistency)

**What it is.** A transfer is a choreographed saga:

```
fraud-detection → [transaction.approved] → account-service debits/credits
                                         → ledger-service records APPROVED
```

When `account-service` refuses to settle an approval (insufficient balance, missing destination), it logs an error and moves no money, but it **does not publish anything**.

**Risk.** There is no money loss or creation, so the risk is not exploitable. It is a **data-consistency** problem: the ledger shows `APPROVED`, and `transaction-service`, which consumes the outcome events, shows the transaction as approved although nothing moved. In a bank this becomes an audit and support issue.

**Fix.** `account-service` publishes `transaction.settlement-failed`; `ledger-service` records it and `transaction-service` marks the transaction `FAILED`. This is the standard saga compensation step and a small change: one producer, one DTO, two consumers.

### 3.4 In-memory login throttling — infrastructure / scale

**What it is.** Failed-login counters live in a `ConcurrentHashMap` inside `auth-service`, keyed by e-mail.

**Limitations.**
- **Multiple replicas:** each instance counts on its own, so N replicas allow about N × 5 attempts.
- **Restart:** the counters reset.
- **Password spraying:** an attacker trying *one* common password across *many* e-mails is never throttled.
- **Account lockout:** anyone can block another user's login for 15 minutes by failing 5 times with that user's e-mail. This is the usual trade-off of per-account throttling.
- **Unbounded memory:** the 10,000-entry cap (`MAX_TRACKED_KEYS`) only triggers removal of *expired* entries. Failed logins for many distinct e-mails within the 15-minute window keep growing the map past the cap, and once it is over the cap every failed login scans the whole map.

**Fix.** Store the counters in Redis with a TTL, which is shared across replicas, survives restarts and bounds memory, and add a per-IP rate limit at `api-gateway`. Until then, a bounded cache (for example Caffeine with `maximumSize` and `expireAfterWrite`) removes the memory issue.

### 3.5 Access tokens cannot be revoked — design (stateless JWT)

**What it is.** Access tokens are self-contained JWTs. Services check signature, expiry and issuer locally and never ask `auth-service` whether a token is still valid. Logout and password change revoke the user's **refresh** tokens, but an access token that was already issued stays valid until it expires (`JWT_EXPIRATION_MS`, 15 minutes by default).

**Risk.** A stolen access token keeps working for up to 15 minutes after the user logs out or changes the password. The same is true for a user who should lose access immediately.

**Current mitigation.** The access token lifetime is short, and the attacker cannot renew it, because the refresh tokens are revoked and a reused refresh token revokes every session.

**Fix, if immediate revocation is needed.**
- A denylist in Redis, keyed by the token id (`jti`) or by user and "not valid before" time, checked by each service. This adds one Redis lookup per request.
- Or token introspection (RFC 7662) against `auth-service` for sensitive routes only, trading latency for immediate revocation.
- Shortening the lifetime further (for example, 5 minutes) reduces the window without adding state.

### 3.6 Tokens are valid in every service (no audience) — design

**What it is.** Tokens carry no `aud` (audience) claim, and no service checks one. A user token issued by `auth-service` is accepted by `account-service`, `transaction-service`, `ledger-service` and `api-gateway` alike. On top of that, `transaction-service` and `ledger-service` forward the caller's own token to `account-service` to check account ownership (`BearerTokenInterceptor`).

**Risk.** If one service is compromised, it can replay the user tokens it receives against every other service, acting as those users until the tokens expire. A token leaked from one service's logs or traffic is equally valid everywhere. Service tokens are less exposed: they are scoped (`users:lookup`) and last 5 minutes.

**Current mitigation.** Short token lifetime (see 3.5). Every service still enforces ownership, so a replayed token only reaches that user's own data.

**Fix.**
- `auth-service` sets an `aud` claim, and each service rejects tokens that were not issued for it (in Spring Security, an audience validator added to the `JwtDecoder`).
- For service-to-service calls, replace token forwarding with **token exchange** (RFC 8693): `transaction-service` trades the user token for a short-lived token whose audience is only `account-service`, still carrying the user's identity.

### 3.7 Concurrent settlement of the same transaction — funds integrity

**What it is.** `BalanceLockService.applyApproval` and `releaseOnDenial` read the balance lock with a plain `SELECT`, check `settled`, set it to `true` and save. There is no row lock (`SELECT ... FOR UPDATE`) and no optimistic version (`@Version`). The Redis idempotency check before them is also a separate read and write, so it does not serialize anything.

**Risk.** Under PostgreSQL's default `READ COMMITTED`, two runs for the same transaction both read `settled = false`; the second `UPDATE` waits for the first and then overwrites it, and both go on to move money. Possible outcomes are a double debit and double credit, or an approval and a denial that both release the reserve, which drives `lockedBalance` below the real reservation and inflates the available balance. Sequential redeliveries are already handled; this needs *concurrent* processing, which happens when a consumer-group rebalance hands an in-flight message to another instance, when `account-service` runs with more than one replica, or when an approval and a denial for the same transaction reach different listener threads.

**Fix.** Claim the settlement atomically: `UPDATE balance_lock SET settled = true WHERE transaction_id = :id AND settled = false`, and move money only if exactly one row was updated. `SELECT ... FOR UPDATE` or `@Version` also work. Cover it with a concurrency test against a real PostgreSQL (Testcontainers).

### 3.8 A transaction can be saved but never published — saga reliability

**What it is.** `transaction-service` saves the transaction and then calls `kafkaTemplate.send(...)` for `transaction.created`. These are two separate writes (a *dual write*), and the future returned by `send` is ignored.

**Risk.** If Kafka is unavailable, the send fails later, or the process stops between the two writes, the client has already received `201` but no event exists: the transaction stays `CREATED` forever, is never analyzed and never reserves funds. Nothing detects or retries it.

**Fix.** The **Transactional Outbox** pattern: write the event to an `outbox` table in the same database transaction as the transaction row, and let a relay (a scheduled publisher or CDC such as Debezium) publish it and mark it sent. As an interim step, at least handle the result of `send` and expose failures as a metric and an alert.

### 3.9 A fraud decision can be lost after a partial failure — saga reliability

**What it is.** `FraudAnalysisService.analyze` calls the ML model, saves the `FraudAnalysis` (unique `transactionId`), publishes the decision (the `send` result is ignored, as in 3.8) and only then marks the event as processed in Redis.

**Risk.** If the publish fails or the process stops after the save, the redelivered `transaction.created` runs the analysis again and fails on the unique constraint. The listener retries until the Kafka error handler gives up, and the decision is never published: the transaction stays `CREATED` and its funds stay reserved.

**Fix.** Make the analysis idempotent: if an analysis already exists for the transaction, republish its stored decision instead of scoring again. Publish decisions through an outbox as in 3.8.

### 3.10 Inconsistent monetary precision — funds integrity

**What it is.** There is no single rule for money:
- `TransactionRequest.amount` and `PixDepositRequest.amount` only check `@NotNull @Positive`, so any number of decimals is accepted.
- `Transaction.amount` has no `precision`/`scale`, so Hibernate's default (scale 2) applies, while `account-service` stores `NUMERIC(19,4)` and the ledger stores the raw event value.
- The ML contract accepts at most 4 decimals.

**Risk.** A transfer of `150.12345` is stored as `150.12` by `transaction-service`, reserved and settled as `150.1235` by `account-service` and recorded as `150.12345` in the ledger. With five or more decimals the ML call is also rejected, which ends in `FLAGGED` (see 3.11). Amounts that disagree across services are an audit problem even when no money is lost.

**Fix.** One money rule enforced at the edge: `@Digits(integer = 17, fraction = 2)` on every amount (BRL has two decimals), reject anything else with `400` instead of rounding, and an explicit `@Column(precision = 19, scale = ...)` on every monetary column.

### 3.11 The historical average breaks the ML contract — availability of the fraud decision

**What it is.** `fraud-detection-service` sends `avgAmountLast30Days` straight from a PostgreSQL `AVG`, which returns many decimals (for example `66.6666666666666667`). `ml-model-service` accepts at most 4 decimals and answers `422`. The client treats any error as unavailability and falls back to a score of `0.50`, which is a `FLAGGED` decision.

**Risk.** This happens in normal use, not under attack: once an account's 30-day average is not exact, every new transfer from it is flagged, and flagged transfers keep their funds reserved (see 3.12). The fallback also hides the contract error, so nothing alerts. Verified with the service's own Pydantic schema: `66.6666666666666667` is rejected and `75.0000000000000000` is accepted.

**Fix.** Round the average to 4 decimals (`setScale(4, RoundingMode.HALF_EVEN)`) before calling the model. Treat `4xx` from the model as a contract bug (logged and alerted) rather than as unavailability. Add a contract test between the Java client and the Python API (see 4).

### 3.12 FLAGGED transactions are never resolved — funds availability

**What it is.** A `FLAGGED` transaction is held for manual review, but there is no review endpoint and `account-service` does not consume `transaction.flagged`.

**Risk.** The reserved amount stays in `lockedBalance` indefinitely and the user cannot spend it. Every ML fallback (model down, or 3.11) produces a `FLAGGED` decision, so this is reachable without any fraud at all.

**Fix.** A manual-review endpoint that approves or denies the transaction by publishing `transaction.approved` or `transaction.denied`, plus a timeout that denies and releases the reservation after a configured period. The review decisions can later become labels for a supervised fraud model.

### 3.13 The ledger exposes internal event payloads — data exposure

**What it is.** `GET /ledger/account/{id}` returns each entry's `eventPayload` exactly as it came from Kafka. The query matches entries where the account is the source *or* the destination.

**Risk.** The **recipient** of a transfer reads the sender's `transaction.created` payload: `ipAddress`, `deviceId`, `idempotencyKey` and `sourceAccountId`, plus the `fraudScore` and decision reason from the outcome events. IP address and device id are personal data under the LGPD, and fraud scores help an attacker tune transfers to stay below the thresholds.

**Fix.** Return a response DTO with explicit fields for a statement (type, amount, status, timestamps, a masked counterparty), chosen by whether the caller is the source or the destination. Never serialize internal event payloads to clients.

### 3.14 Ledger idempotency depends on a Redis key with a TTL — data consistency

**What it is.** Each ledger consumer checks `processed:ledger-service:{eventId}` in Redis, saves the entry to MongoDB, and then sets the key with a 24-hour TTL. MongoDB has no unique index on the event id.

**Risk.** A crash between the save and the Redis write, a Redis flush or data loss, or a replay older than 24 hours (for example, after resetting consumer offsets) records the same event twice, and the statement shows duplicated entries. No money moves, because `account-service` settles against its own database flag.

**Fix.** A unique index on `eventId` in MongoDB and an insert that treats a duplicate-key error as "already processed". Redis then becomes an optional fast path, not the source of truth.

### 3.15 The IP used for fraud scoring comes from the client — fraud-signal integrity

**What it is.** `ipAddress` (and `deviceId`) are fields of the `POST /transactions` body. `fraud-detection-service` uses them for the `isForeignIp` and `isNewDevice` features. A missing, invalid or IPv6 address is treated as not foreign.

**Risk.** The caller controls the signals meant to catch them: a fraudster sends a domestic IPv4 address, or none at all, and lowers the score.

**Fix.** Derive the client IP on the server: `api-gateway` sets `X-Forwarded-For`, and `transaction-service` trusts that header only from the gateway (`server.forward-headers-strategy` with a trusted-proxy list). Support IPv6, and treat an unknown IP as a risk signal of its own instead of as "domestic". A device id always comes from the client, so it should be treated as a weak signal (or replaced by a signed device binding in a real app).

### 3.16 PIX key ownership is not verified — identity

**What it is.** Registration does not verify that the user controls the e-mail address, and the CPF is only checked for a valid check digit. `EMAIL` and `CPF` PIX keys are created from these profile values.

**Risk.** Someone can register with another person's e-mail or CPF and claim the PIX key first. Deposits addressed to that key then go to the wrong account, and key uniqueness stops the real owner from registering it.

**Fix.** Confirm the e-mail with a link or code before an `EMAIL` key can be created. A real system also validates the CPF against the tax authority and supports the central bank's key-claim (portability) flow. This is also why `PHONE` keys are not offered until phone numbers are verified.

### Summary

| # | Risk | Type | Exploitable today | When to address |
|---|---|---|---|---|
| 3.1 | Kafka and Redis without auth | Infrastructure | No (localhost only) | Phase 3, with Kubernetes |
| 3.2 | Public deposit | Design (intentional) | Yes, by design of the demo | Before any non-demo use |
| 3.3 | No compensation event | Design | No (data consistency only) | Next saga iteration |
| 3.4 | In-memory login throttling | Infrastructure / scale | Partially (spraying, multi-replica) | When running more than one replica |
| 3.5 | Access tokens cannot be revoked | Design (stateless JWT) | Only with a stolen token, for up to 15 min | If immediate logout/revocation is required |
| 3.6 | No audience: tokens valid in every service | Design | Only from a compromised service or leaked token | Before running untrusted or third-party services |
| 3.7 | Concurrent settlement of the same transaction | Funds integrity | Needs concurrent processing (rebalance, replicas) | Next: atomic settlement claim |
| 3.8 | Transaction saved but `transaction.created` never published | Saga reliability | No (failure-driven) | With the Transactional Outbox |
| 3.9 | Fraud decision lost after a partial failure | Saga reliability | No (failure-driven) | With the Transactional Outbox |
| 3.10 | Inconsistent monetary precision | Funds integrity | Yes, by sending more decimals | Next: single money rule |
| 3.11 | Historical average breaks the ML contract | Availability | Happens in normal use | Next, together with 3.10 |
| 3.12 | FLAGGED transactions never resolved | Funds availability | Reachable without fraud (via 3.11 or ML down) | With the manual-review flow |
| 3.13 | Ledger exposes internal event payloads | Data exposure (LGPD) | Yes, by any transfer recipient | Soon |
| 3.14 | Ledger idempotency depends on a Redis TTL | Data consistency | No (failure- or replay-driven) | With the reliability work (3.8/3.9) |
| 3.15 | Client-supplied IP in fraud scoring | Fraud-signal integrity | Yes, by the sender | Soon |
| 3.16 | PIX key ownership not verified | Identity | Yes, by registering someone else's e-mail or CPF | Before any non-demo use |

---

## 4. Engineering gaps

These are not vulnerabilities by themselves, but they fall short of standard practice and make the risks above harder to fix or detect.

| Gap | Where | Why it matters | Fix |
|---|---|---|---|
| Schema managed by `ddl-auto: update` | Every JPA service | No versioned, reviewable migrations; renames and drops are not handled; no rollback path. | Flyway migrations per service, with `ddl-auto: validate`. |
| Enums stored as ordinals | `Transaction.type` and `Transaction.status` (no `@Enumerated`) | The database stores positions, so inserting or reordering an enum constant silently changes the meaning of existing rows. `PixKey` and `FraudAnalysis` already use `EnumType.STRING`. | `@Enumerated(EnumType.STRING)`, migrating the existing values. |
| Missing indexes | Transaction history by account, `fraud_analysis` velocity and average queries by source account and time, ledger queries on `eventPayload.sourceAccountId`/`destinationAccountId` | These queries scan whole tables or collections as data grows; only `tb_refresh_tokens.user_id` and the unique constraints are indexed. | Indexes created by the migrations (Flyway) and Mongo index definitions. |
| No HTTP client timeouts | `account-service` → `auth-service`, `transaction-service` and `ledger-service` → `account-service` | Only the ML call has a timeout (Resilience4j, 3 s). A slow dependency holds request threads until they run out. | Connect and read timeouts on every `RestClient`, plus a circuit breaker on the synchronous calls. |
| No contract tests between services | Kafka events between the Java services; the Java → Python ML call | Producers and consumers can drift without a failing build. 3.11 is exactly such a break. | Consumer-driven contract tests (Spring Cloud Contract or Pact), or a shared JSON Schema tested on both sides. |

---

## 5. Verifying

The security boundaries are covered by unit and `@WebMvcTest` tests in each service (`*SecurityTest*`, `BalanceLockServiceTest`, `TransactionServiceTest`, `LoginAttemptServiceTest`, …). They do not need a database:

```bash
cd account-service && ./mvnw test -Dtest='*Security*,BalanceLockServiceTest'
```

The full flow was verified against the Docker stack: registration, login, accounts, deposit, transfer, the saga through the ledger, forged headers, tampered and service tokens, IDOR attempts, overdrafts, duplicate and forged approvals published directly to Kafka, and login throttling.
