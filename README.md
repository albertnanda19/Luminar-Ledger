# Luminar-Ledger

High-throughput distributed double-entry accounting engine with correctness-first write-path semantics.

---

## 2. Project Overview

Luminar-Ledger is a backend service for recording and settling financial transactions using a double-entry ledger model.

Ledger systems cannot be treated like generic CRUD because the primary responsibility is not “persisting a request” but preserving financial invariants under concurrency, retries, and partial failures. In practice, that means:

- The write-path must be strongly consistent and concurrency-safe.
- Posting must be idempotent and deterministic under retries.
- Every state transition must be auditable and reconstructible.

This repository implements these requirements using Java 21 + Spring Boot, PostgreSQL for transactional storage, and Redis as an optional best-effort accelerator.

---

## 3. Core Principles & Guarantees

- **Double-entry accounting**
  - Each transaction is recorded as balanced debit/credit entries.
  - Balance updates are derived from entries, not ad-hoc mutations.

- **Strict ACID transactions**
  - Ledger posting is executed inside database transactions.
  - Either all ledger effects commit or none do.

- **Serializable isolation + retry**
  - The posting path uses PostgreSQL `SERIALIZABLE` isolation.
  - Serialization conflicts are handled with bounded retries at the application layer.

- **No ghost money invariant**
  - The system is designed to prevent value appearing/disappearing due to races or partial updates.
  - Correctness is prioritized over raw throughput.

- **Deterministic idempotency**
  - Client-provided `referenceKey` is the idempotency key.
  - Duplicate requests are prevented from double-posting.
  - Completed requests can be replayed deterministically without re-entering the DB write-path.

- **Immutable audit trail**
  - Posting emits immutable ledger events suitable for reconciliation and audit.
  - The event log is append-oriented and treated as authoritative history.

- **Event sourcing (ledger posting only)**
  - The system persists domain events for the ledger posting boundary.
  - This is intentionally scoped; not all read models are fully event-sourced.

- **Async projection**
  - Read models (e.g., transaction history) are built asynchronously from ledger events.
  - This keeps the write-path lean and avoids coupling reads to write transactions.

- **Separation of write-path & read-path**
  - Write-path optimizes for correctness and isolation.
  - Read-path optimizes for stable query patterns via projections and caches.

- **Graceful degradation (Redis optional)**
  - Redis is used for global idempotency and read-through caching.
  - Redis failures are non-fatal: the system falls back to the DB path (best-effort cache).

- **Explicit failure handling**
  - Failure states are surfaced deterministically (e.g., idempotency “in progress” conflict).
  - The system prefers explicit, auditable states over implicit “magic retries.”

---

## 4. High-Level Architecture

### Write path

- `POST /api/v1/transactions` receives a posting request.
- The request includes a client-defined `referenceKey` (idempotency key).
- **Global idempotency guard (Redis)** runs before entering the transactional DB path:
  - Acquire atomically (`SET NX + TTL` via Lua to avoid race windows).
  - If `IN_PROGRESS`: reject early with HTTP 409.
  - If `COMPLETED`: replay the cached `PostedTransaction` response without opening a DB transaction.
  - If Redis is unavailable: proceed with DB path (best-effort).
- The posting service executes under `SERIALIZABLE` isolation.
- On success, the idempotency record is marked `COMPLETED` (with response summary).
- On failure, the idempotency record is marked `FAILED` (retry is allowed later).

### Event store

- Successful postings append immutable ledger events in PostgreSQL.
- These events are the source stream for projections.

### Projection

- A projector consumes ledger events and materializes query-focused tables.
- Projections are asynchronous and can be replayed.

### Read path

- Read endpoints query projection tables.
- A Redis read-through cache is used for hot history queries.

### Cache layer

- Redis acts as a best-effort accelerator:
  - Global idempotency for write-path.
  - Read-through cache for transaction history.

---

## 5. Key Features

- **Account management (multi-currency)**
  - Create accounts.
  - Operational state transitions: freeze, unfreeze, close.

- **Atomic double-entry transactions**
  - Entries and balance updates are committed atomically.

- **Concurrency torture-tested ledger posting**
  - Integration tests exercise high-contention posting to expose anomalies.

- **Global idempotency protection**
  - Redis-backed idempotency guard prevents duplicate postings across nodes.
  - Deterministic replay for completed requests.

- **Event-sourced ledger events**
  - Posting produces immutable ledger events stored in PostgreSQL.

- **Async transaction history projection**
  - History is materialized asynchronously from ledger events.

- **Redis read-through cache for history**
  - Reduces DB load and tail latency for repeated history queries.

- **Soft-delete & account freezing**
  - Operational controls to block posting from frozen/closed accounts.

- **Comprehensive audit trail**
  - Append-oriented persistence suitable for audit/reconciliation workflows.

---

## 6. Project Structure

The codebase uses a layered architecture with explicit separation of concerns:

- **`io.luminar.ledger.domain`**
  - Domain models, invariants, and domain errors.

- **`io.luminar.ledger.application`**
  - Use-cases and orchestration.
  - Retry logic for serialization failures.
  - Global idempotency guard integration.

- **`io.luminar.ledger.service`**
  - Write-path core transactional services.

- **`io.luminar.ledger.infrastructure`**
  - Persistence and integrations (JPA repositories, projector, Redis interactions).

- **`io.luminar.ledger.api`**
  - REST controllers, DTOs, and exception mapping.

---

## 7. Getting Started

### 7.1 Prerequisites

- **Java**: 21
- **Build tool**: Maven (use the wrapper `./mvnw`)
- **PostgreSQL**: required (strong consistency, audit store, projections)
- **Redis**: optional (recommended for idempotency + caching)
- **Docker**: recommended for local infrastructure and required for Testcontainers integration tests

---

### 7.2 Clone Repository

SSH:

```bash
git clone git@github.com:albertnanda19/Luminar-Ledger.git
cd Luminar-Ledger
```

HTTPS:

```bash
git clone https://github.com/albertnanda19/Luminar-Ledger.git
cd Luminar-Ledger
```

---

### 7.3 Environment Configuration

The default configuration is in `src/main/resources/application.yaml`.

**Database (PostgreSQL)**

- URL: `jdbc:postgresql://localhost:5432/luminar_ledger`
- Username: `postgres`
- Password: `password`
- Hibernate schema generation is disabled (`ddl-auto: none`).

**Flyway**

- Enabled by default (`spring.flyway.enabled: true`).
- `clean` is disabled (`spring.flyway.clean-disabled: true`).

**Redis (optional)**
Redis properties are not hardcoded in `application.yaml`. Configure via standard Spring properties:

- `spring.data.redis.host`
- `spring.data.redis.port`

**Default HTTP port**
The port is not explicitly configured; Spring Boot defaults to **8080**.

---

### 7.4 Run Infrastructure

You need PostgreSQL. Redis is optional but recommended.

PostgreSQL example:

```bash
docker run --name luminar-postgres \
  -e POSTGRES_DB=luminar_ledger \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 \
  -d postgres:16
```

Redis example (optional):

```bash
docker run --name luminar-redis \
  -p 6379:6379 \
  -d redis:7-alpine
```

---

### 7.5 Run Database Migration

Flyway migrations are executed automatically on application startup.

---

### 7.6 Run Application

Run the Spring Boot application:

```bash
./mvnw spring-boot:run
```

The service will start on `http://localhost:8080` by default.

---

### 7.7 Verify Application

Health check (Actuator):

```bash
curl -s http://localhost:8080/actuator/health
```

Minimal API flow:

- Create an account: `POST /api/v1/accounts`
- Post a transaction: `POST /api/v1/transactions`
- Query account history: `GET /api/v1/accounts/{accountId}/transactions`

---

## 8. Concurrency & Correctness Testing

The project includes correctness-focused integration tests designed to surface race conditions and anomalies that are easy to miss in unit tests.

- `SERIALIZABLE` is used to make concurrency anomalies explicit.
- Bounded retries handle expected serialization conflicts under contention.
- “Test PASS” here is meaningful in a financial sense: it indicates that invariants (idempotency, balance correctness, auditability) hold under concurrency.

Integration testing uses **Testcontainers** for PostgreSQL and Redis.

---

## 9. Design Decisions & Trade-offs

- **Why not full CQRS (yet)**
  - The system separates write and read paths, but does not claim a fully independent CQRS stack.
  - This keeps operational complexity controlled while still enabling projection-based reads.

- **Why event sourcing only for ledger posting**
  - Posting is the correctness boundary and benefits most from an immutable event stream.
  - Not all read models need full event sourcing; projections are sufficient for query workloads.

- **Why Redis is optional**
  - Redis improves contention characteristics (idempotency guard) and read performance (cache), but must not be a single point of failure.
  - When Redis is down, the system continues via database protections.

- **Why strong consistency over raw throughput**
  - Financial systems optimize for correctness, auditability, and deterministic behavior.
  - Throughput improvements are only accepted when invariants remain explicit and provable.

---

## 10. Future Evolution

- **Outbox pattern** for reliable event publication.
- **Kafka integration** for downstream consumers and streaming projections.
- **CQRS read models** with dedicated query stores.
- **Multi-tenant partitioning** strategies.
- **Multi-region replication** with explicit consistency and reconciliation design.

---

## 11. Author

Albert Mangiri
