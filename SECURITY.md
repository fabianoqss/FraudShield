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
- Each transaction is settled at most once. Balance locks are kept and marked `settled`, so redelivered approvals or denials are ignored, and a lock that arrives after its approval is not applied.
- Settlement never debits more than was reserved or is still available, and never debits when the destination is missing.
- Kafka consumers keep their idempotency keys in Redis under their own namespace (`processed:{service}:{eventId}`).

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

**Current mitigation.** All ports are bound to `127.0.0.1`, so only the host machine can reach them.

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

**Risk.** There is no money loss or creation, so the risk is not exploitable. It is a **data-consistency** problem: the ledger shows `APPROVED`, and once `transaction-service` consumes outcome events it will show the transaction as approved although nothing moved. In a bank this becomes an audit and support issue.

**Fix.** `account-service` publishes `transaction.settlement-failed`; `ledger-service` records it and `transaction-service` marks the transaction `FAILED`. This is the standard saga compensation step and a small change: one producer, one DTO, two consumers.

### 3.4 In-memory login throttling — infrastructure / scale

**What it is.** Failed-login counters live in a `ConcurrentHashMap` inside `auth-service`, keyed by e-mail.

**Limitations.**
- **Multiple replicas:** each instance counts on its own, so N replicas allow about N × 5 attempts.
- **Restart:** the counters reset.
- **Password spraying:** an attacker trying *one* common password across *many* e-mails is never throttled.
- **Account lockout:** anyone can block another user's login for 15 minutes by failing 5 times with that user's e-mail. This is the usual trade-off of per-account throttling.

**Fix.** Store the counters in Redis, which is shared across replicas and survives restarts, and add a per-IP rate limit at `api-gateway`.

### Summary

| # | Risk | Type | Exploitable today | When to address |
|---|---|---|---|---|
| 3.1 | Kafka and Redis without auth | Infrastructure | No (localhost only) | Phase 3, with Kubernetes |
| 3.2 | Public deposit | Design (intentional) | Yes, by design of the demo | Before any non-demo use |
| 3.3 | No compensation event | Design | No (data consistency only) | Next saga iteration |
| 3.4 | In-memory login throttling | Infrastructure / scale | Partially (spraying, multi-replica) | When running more than one replica |

---

## 4. Verifying

The security boundaries are covered by unit and `@WebMvcTest` tests in each service (`*SecurityTest*`, `BalanceLockServiceTest`, `TransactionServiceTest`, `LoginAttemptServiceTest`, …). They do not need a database:

```bash
cd account-service && ./mvnw test -Dtest='*Security*,BalanceLockServiceTest'
```

The full flow was verified against the Docker stack: registration, login, accounts, deposit, transfer, the saga through the ledger, forged headers, tampered and service tokens, IDOR attempts, overdrafts, duplicate and forged approvals published directly to Kafka, and login throttling.
