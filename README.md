# FraudShield

A real-time payment processing platform with fraud detection, built as a distributed, event-driven microservices system.

> **Portfolio project.** There are no real bank integrations — every payment, account, and transaction is simulated. The goal is to demonstrate distributed systems design, event-driven architecture, ML in production, and Kubernetes.

---

## What it does

FraudShield simulates a digital bank's payment pipeline:

1. A transaction is created (e.g. a PIX transfer).
2. It's analyzed for fraud in real time by an ML-backed scoring service.
3. Based on the fraud score, the transaction is **approved**, **flagged** for manual review, or **denied**.
4. Every step is recorded as an immutable ledger entry.

The system is built as independent services communicating over REST (synchronous) and Kafka (asynchronous), each owning its own database.

---

## Architecture

| Service | Responsibility | Port |
|---|---|---|
| `api-gateway` | Single entry point, JWT validation (JWKS), routing | 8080 |
| `auth-service` | Registration, login, refresh tokens, RS256 JWT issuance, JWKS, service tokens | 8081 |
| `account-service` | Account balances, balance locking | 8082 |
| `transaction-service` | Transaction creation and status | 8083 |
| `fraud-detection-service` | Orchestrates fraud scoring and decisions | 8084 |
| `ml-model-service` | ML inference (fraud score) — Python/FastAPI | 8085 |
| `ledger-service` | Append-only audit trail of all events (MongoDB) | 8086 |
| `notification-service` | User notifications on transaction outcome | 8087 |

**Transaction flow (Saga, choreography-based via Kafka):**

```
transaction-service → [transaction.created] → fraud-detection-service
                                                       │
                        ┌──────────────────────────────┼───────────────────────────────┐
                        ▼                               ▼                               ▼
              [transaction.approved]           [transaction.flagged]           [transaction.denied]
                        │                               │                               │
        ┌───────────────┼───────────────┐               │               ┌───────────────┼───────────────┐
        ▼               ▼               ▼               ▼               ▼               ▼               ▼
 account-service  ledger-service  notification-svc  ledger-service  notification-svc  ledger-service  notification-svc
```

> `transaction-service` also consumes the three outcome events and moves the transaction from `CREATED` to `APPROVED`, `FLAGGED` or `DENIED`.

---

## Security model

> Full details, the list of fixed vulnerabilities and the open risks: [`SECURITY.md`](./SECURITY.md).

Every HTTP-facing service is an **OAuth2 Resource Server** and validates the JWT itself. No service trusts identity headers such as `X-User-Id` — a request that reaches a service port directly still needs a valid token.

- **Signing:** `auth-service` signs access tokens with **RS256**. The private key (`JWT_PRIVATE_KEY`) lives only in `auth-service`.
- **Verification:** the public key is published at `GET /.well-known/jwks.json`. `api-gateway`, `account-service`, `transaction-service` and `ledger-service` fetch it via `AUTH_JWKS_URI` and check signature, expiry and issuer (`iss=fraudshield-auth-service`).
- **Token types:** every token carries `token_type`.
  - `user` — issued on login/refresh; `sub` is the user's UUID. Required (`ROLE_USER`) on all user routes.
  - `service` — issued by `POST /auth/service-token` (client credentials) with scopes. Used today only by `account-service` to call `GET /auth/users/lookup` (scope `users:lookup`) while resolving a PIX key. A service token on a user route gets `403`.
- **Gateway:** forwards the `Authorization: Bearer` header untouched and blocks the internal endpoints (`/auth/service-token`, `/auth/users/lookup`) from the outside.
- **Service-to-service calls:** `transaction-service` and `ledger-service` propagate the caller's user token when checking account ownership with `account-service`.
- **Public routes:** `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/accounts/deposit` (simulated PIX deposit), `/actuator/**`.
- **Ownership:** users only see their own accounts, ledgers and transactions (as source or destination owner). Anything else returns `403`/`404`. The public deposit response carries only the receiver's name and the amount.
- **Brute force:** after 5 failed logins for the same e-mail, `/auth/login` answers `429` for 15 minutes (in-memory, per `auth-service` instance).
- **Funds integrity:** a transfer is rejected up front if it exceeds the available balance or targets an unknown account. `account-service` reserves funds with a conditional update, settles each transaction at most once (redelivered events are ignored) and never debits more than is reserved or available.
- **Local infrastructure:** every port published by `docker-compose` is bound to `127.0.0.1`.

### Known risks
Sixteen risks remain open. They are described in detail, with scenarios and fixes, in [`SECURITY.md`](./SECURITY.md#3-known-risks), which also lists the engineering gaps (migrations, enum storage, indexes, timeouts, contract tests):
- Kafka and Redis run without authentication (mitigated: bound to localhost; fix in Phase 3).
- The public PIX deposit can credit any key, up to 10000.00 per deposit. This is intentional, to simulate incoming transfers.
- A failed settlement publishes no compensation event (data consistency, not exploitable).
- Login throttling is in memory and per instance, and its map is not effectively bounded.
- Access tokens stay valid until they expire (15 min), even after logout or a password change.
- Tokens have no audience, so a token accepted by one service is accepted by all of them.
- Concurrent processing of the same transaction can settle it twice.
- A transaction can be saved without its event being published, and a fraud decision can be lost after a partial failure.
- Amounts are not held to a single precision across services.
- A non-exact historical average is rejected by the ML contract, so the transfer ends up `FLAGGED`.
- `FLAGGED` transactions are never resolved, so their funds stay reserved.
- The ledger returns internal event payloads, including the sender's IP and device id, to the recipient.
- Ledger idempotency depends on a Redis key with a 24-hour TTL.
- The IP used for fraud scoring comes from the client.
- E-mail and CPF ownership are not verified before they become PIX keys.

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1 |
| Gateway | Spring Cloud Gateway |
| Security | Spring Security, JWT |
| Messaging | Apache Kafka |
| Databases | PostgreSQL (one instance per service) |
| Cache / Idempotency | Redis |
| ML service | Python 3.11, FastAPI, scikit-learn |
| Containers / Orchestration | Docker, Docker Compose, Kubernetes (Minikube) |
| Observability | Prometheus, Grafana, OpenTelemetry |
| Build | Maven |

---

## Getting started

### Prerequisites
- Java 21
- Maven
- Docker + Docker Compose
- Python 3.11 (for `ml-model-service`, Phase 2)

### 1. Configure environment variables
```bash
cp .env.example .env
# fill in database credentials, then generate the auth secrets:
echo "JWT_PRIVATE_KEY=$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 -w0)" >> .env
echo "SERVICE_CLIENT_ACCOUNT_SECRET=$(openssl rand -base64 32)" >> .env
```
If `JWT_PRIVATE_KEY` is left empty, `auth-service` generates an ephemeral key on startup and logs a warning — fine for a quick local run, but every token becomes invalid when it restarts.

### 2. Start local infrastructure (PostgreSQL, Kafka, Redis)
`.env` lives at the repo root but Docker Compose runs from `infrastructure/`, so point it at the env file explicitly with `--env-file`:
```bash
cd infrastructure
docker compose --env-file ../.env up -d
docker compose ps   # all containers should show "Up (healthy)"
```

### 3. Run a service
```bash
cd auth-service
./mvnw spring-boot:run
```
The service reads the same variables from `.env` — in IntelliJ, set them under Run Configuration → Environment variables (or use the EnvFile plugin to load `.env` directly).

Each service is a standalone Spring Boot (or FastAPI) app and can be run independently once its dependencies (its database, Kafka) are up. Start `auth-service` first: the other services fetch its JWKS to validate tokens.

To run the whole stack in Docker instead:
```bash
cd infrastructure
docker compose --env-file ../.env --profile services up -d --build
```

### 4. Run the tests
```bash
cd auth-service
./mvnw test
```
No local infrastructure or `.env` is needed: the `@SpringBootTest` tests (context loads, OpenAPI specs, integration tests) start their own PostgreSQL, MongoDB, Kafka and Redis with Testcontainers, so they only need Docker running. The security, client and unit tests need nothing at all. CI (GitHub Actions) runs the suites of the services a pull request touches.

---

## Project structure

```
FraudShield/
├── api-gateway/               # Spring Cloud Gateway (WebMVC)
├── auth-service/
├── account-service/
├── transaction-service/
├── fraud-detection-service/
├── ledger-service/
├── notification-service/
├── ml-model-service/          # FastAPI /predict, shared features, training, trained model; research/paysim baseline
└── infrastructure/            # docker-compose, Prometheus, Grafana, OpenTelemetry Collector, Tempo
```

### 5. API documentation (Swagger UI)
With the gateway and the services running, open **http://localhost:8080/swagger-ui.html** and pick a service in the top-right selector. Each service publishes its OpenAPI spec at `/v3/api-docs` (springdoc); the gateway serves them at `/api-docs/{auth,account,transaction,ledger}` and hosts the only Swagger UI. "Try it out" calls go through the gateway: log in with `POST /auth/login`, then paste the `accessToken` into **Authorize**. Internal routes (`/internal/**`, `/auth/service-token`, `/auth/users/lookup`) are left out of the specs.

The docs are on by default for local runs and off in `docker-compose` (`API_DOCS_ENABLED=false`), since a published spec maps the whole API for an attacker.

---

## Status

**Phase 1 — Core flow**
- [x] Project structure and `docker-compose` (databases, Kafka, Redis, services)
- [x] `auth-service` — register, login, refresh-token rotation, logout, password change, RS256 + JWKS, service tokens
- [x] `account-service` — accounts, balance locks, simulated PIX deposit, consumers for `transaction.created/approved/denied`
- [x] `transaction-service` — creation with idempotency key and ownership check, lookup, paginated history per account, publishes `transaction.created`
- [x] `fraud-detection-service` — consumes `transaction.created`, builds features, scores, persists and publishes `approved/flagged/denied`
- [x] Per-service JWT validation (Resource Server) — replaces the trusted `X-User-Id` header
- [x] `transaction-service` consuming outcome events to update the transaction status
- [ ] Handling of `transaction.flagged` (balance lock release) and a manual-review endpoint
- [x] Ownership check on `GET /transactions/{id}`, balance checks and single settlement in the saga
- [ ] Automated tests beyond security and unit level — every service boots its context against Testcontainers, and there are integration tests for the PIX key registry and the `ledger-service`/`notification-service` consumers; the end-to-end saga is not covered yet

**Phase 2 — ML and supporting services**
- [x] `ledger-service` — MongoDB append-only log, 4 Kafka consumers, Redis idempotency, REST API
- [x] `api-gateway` — routes `/auth/**`, `/accounts/**`, `/transactions/**`, `/ledger/**`
- [x] OpenAPI specs per service (springdoc), aggregated in one Swagger UI at the gateway
- [x] `ml-model-service` — `POST /predict` with the same contract `fraud-detection-service` sends, RandomForest trained on synthetic data (features shared by training and serving), `/health`, Prometheus `/metrics`. The earlier PaySim baseline is kept under `research/paysim/` for reference; its features do not match the serving contract.
- [x] `notification-service` — consumes `approved/flagged/denied`, Redis idempotency, DLT for malformed messages (notifications are logged, no e-mail/SMS yet)

**Phase 3 — Operations**
- [x] Prometheus + Grafana, OpenTelemetry tracing to Tempo
- [ ] Kubernetes manifests (and restricting direct access to service ports)
- [ ] ML model training pipeline (retraining today is manual: `python -m ml.training.train`)

---

## Configuration reference

| Variable | Used by | Purpose |
|---|---|---|
| `JWT_PRIVATE_KEY` | auth-service | Base64 PKCS#8 DER RSA private key used to sign tokens |
| `JWT_ISSUER` | all JWT services | Expected `iss` claim (default `fraudshield-auth-service`) |
| `JWT_EXPIRATION_MS` | auth-service | Access-token lifetime (default 15 min) |
| `AUTH_JWKS_URI` | gateway, account, transaction, ledger | Where to fetch the public keys |
| `SERVICE_CLIENT_ACCOUNT_SECRET` | auth-service, account-service | Client secret for `account-service`'s service token |
| `API_DOCS_ENABLED` | gateway, auth, account, transaction, ledger | Serve the OpenAPI specs and the Swagger UI (default `true`; `false` in `docker-compose`) |
