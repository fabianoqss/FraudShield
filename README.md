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
| `api-gateway` | Single entry point, JWT validation, routing | 8080 |
| `auth-service` | User registration, login, JWT issuance | 8081 |
| `account-service` | Account balances, balance locking | 8082 |
| `transaction-service` | Transaction creation and status | 8083 |
| `fraud-detection-service` | Orchestrates fraud scoring and decisions | 8084 |
| `ml-model-service` | ML inference (fraud score) — Python/FastAPI | 8085 |
| `ledger-service` | Append-only audit trail of all events | 8086 |
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

Full event contracts, DB schemas, and package structure are documented in [`CLAUDE.md`](./CLAUDE.md).

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3 |
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

### 1. Start local infrastructure (Kafka, PostgreSQL, Redis)
```bash
cd infrastructure
docker-compose up -d
docker-compose ps   # all containers should show "Up"
```

### 2. Configure environment variables
```bash
cp .env.example .env
# fill in JWT_SECRET and database credentials
```

### 3. Run a service
```bash
cd services/auth-service
./mvnw spring-boot:run
```

Each service is a standalone Spring Boot (or FastAPI) app and can be run independently once its dependencies (its database, Kafka) are up.

---

## Project structure

```
fraudshield-backend/
├── services/            # One folder per microservice
├── infrastructure/      # docker-compose, Kafka config, Kubernetes manifests
├── ml/                  # Notebooks, training scripts, trained models
├── docs/architecture/   # Diagrams and ADRs
└── CLAUDE.md            # Full technical spec: contracts, schemas, conventions
```

---

## Status

**Phase 1 — In progress**
- [x] Project structure defined
- [x] Infrastructure `docker-compose` defined
- [ ] `auth-service`
- [ ] `account-service`
- [ ] `transaction-service`
- [ ] `fraud-detection-service` (basic)
- [ ] Kafka integration between `transaction-service` and `fraud-detection-service`

**Phase 2 — Not started**
- [ ] `ml-model-service` (Python + scikit-learn)
- [ ] `ledger-service`
- [ ] `notification-service`
- [ ] `api-gateway`

**Phase 3 — Not started**
- [ ] Kubernetes manifests
- [ ] Prometheus + Grafana
- [ ] OpenTelemetry
- [ ] ML model training pipeline

---

## Documentation

Full architectural decisions, Kafka event contracts, database schemas, and coding conventions live in [`CLAUDE.md`](./CLAUDE.md).
