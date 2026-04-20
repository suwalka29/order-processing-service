# Order Processing Service

A production-aware backend service for a food delivery platform, implementing
order placement, status management, and event-driven coordination via Kafka.

**Stack:** Java 17 · Spring Boot 3.2 · PostgreSQL 16 · Apache Kafka · Flyway · Docker

---

## How to run

```bash
# 1. Start all infrastructure (Postgres + Kafka + Zookeeper) and the app
docker-compose up -d

# 2. Verify all containers are healthy
docker ps

# 3. App is available at http://localhost:8080
# Postgres:  localhost:5432  (DB: orders_db, user: orderuser, pass: orderpass)
# Kafka:     localhost:9092
```

The app container waits for Postgres and Kafka health checks before starting.
Flyway runs migrations automatically on startup — no manual schema setup needed.

### Running locally without Docker (development mode)

```bash
# Start only infra
docker-compose up -d postgres kafka zookeeper

# Run the Spring Boot app directly
./gradlew bootRun

# Or build and run the jar
./gradlew clean bootJar
java -jar build/libs/order-service-*.jar
```

---

## API overview

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/orders` | Create order (requires `Idempotency-Key` header) |
| `GET` | `/api/v1/orders/{orderId}` | Fetch single order with items |
| `GET` | `/api/v1/orders` | Paginated list with optional `customerId` + `status` filters |
| `PATCH` | `/api/v1/orders/{orderId}/status` | Update order status |

All endpoints require `X-Customer-Id` header (simplified auth context — no real
authentication is implemented; see Known Limitations).

See `api-tests.http` for runnable examples of every endpoint, edge case, and
error scenario.

---

## Design decisions

### Idempotency

The `Idempotency-Key` header value and the serialized order response are written
to the `idempotency_keys` table **within the same DB transaction** as the order
insert. Both the order row and the idempotency record commit atomically — there
is no window where the order exists but the key does not, or vice versa.

On a duplicate request, the stored response body is deserialized and returned
directly. The response is always identical to the original (same order UUID, same
`totalAmount`, same `PENDING` status) regardless of the current order state.

**Race condition handling:** Two simultaneous requests with the same key may both
pass the initial `findById` check before either has stored the key. When the
second request tries to `INSERT` into `idempotency_keys`, the PostgreSQL unique
constraint on the primary key fires a `DataIntegrityViolationException`. The
catch block re-reads the key (now committed by the first request) and returns
the stored response. The database unique constraint acts as the distributed mutex
— this works correctly across all service replicas without any in-memory state.

### Authorization

`X-Customer-Id` is treated as the authenticated customer identity throughout the
service. Ownership is enforced at the service layer:

- **Order retrieval:** only the customer who placed the order may fetch it.
- **Cancellation:** only the customer who placed the order may cancel it.
- **Forward transitions (CONFIRMED, PREPARING, etc.):** in a real system, these
  would be called by an internal service (kitchen/delivery system) via
  service-to-service auth. For this implementation, the `X-Customer-Id` header
  is accepted but ownership is only strictly enforced for cancellation — the
  assignment specifies the status endpoint is called by an internal service.

Any unauthorized access attempt returns `403 Forbidden`.

### Concurrency — optimistic locking

The `orders` table has a `version BIGINT` column. JPA's `@Version` annotation
enables optimistic locking: when two concurrent requests read an order at
`version=5` and both attempt to save, only one `UPDATE ... WHERE version=5`
succeeds. The other receives `ObjectOptimisticLockingFailureException`, which is
mapped to `409 Conflict` with the message "Order was modified concurrently.
Please retry."

**Why optimistic over pessimistic (`SELECT FOR UPDATE`):**
Status updates are infrequent and short-lived. Conflicts are rare, not the
common case. Optimistic locking avoids held locks, scales better under load,
and eliminates deadlock risk. Pessimistic locking would be appropriate for
inventory systems where thousands of users compete for the same row simultaneously.

### Kafka consistency — AFTER_COMMIT listener

DB writes and Kafka publishes are kept consistent using
`@TransactionalEventListener(phase = AFTER_COMMIT)`. The Spring
`ApplicationEvent` is published inside the DB transaction; the Kafka publish
fires only after the transaction has committed successfully.

**What this prevents:** phantom events — no Kafka message is published for a
transaction that rolled back. A spurious "your order is confirmed" notification
is a worse user experience than a delayed one.

**Known trade-off:** a JVM crash between DB commit and listener execution
permanently loses the Kafka event. The DB reflects the correct state; Kafka does
not. The production fix is the Transactional Outbox Pattern (see ADR-001 and
What I'd do differently).

### Status idempotency — internal service retries

The status update endpoint may be called up to 3 times by the internal service
for the same transition. Before attempting any state change, the service checks:

```java
if (order.getStatus() == newStatus) {
    return OrderResponse.from(order); // 200 OK, no DB write, no Kafka event
}
```

This ensures retries are safe: no duplicate DB writes, no duplicate Kafka events,
and no incorrect `409 Conflict` errors for an operation that already succeeded.

---

## Failure analysis

**Scenario:** the service crashes after the DB write succeeds but before the
Kafka publish fires.

**State of the system:**
- PostgreSQL: order record exists with the new status. The `version` column is
  incremented. If this was order creation, the `idempotency_keys` record exists.
- Kafka: no event published to `order-status-changed`.
- Downstream consumers (notification service, delivery service): unaware of
  the status change.

**Client retry behaviour:**

For order creation retries, the client resends `POST /api/v1/orders` with the
same `Idempotency-Key`. The key is found in the `idempotency_keys` table and the
stored `201` response is returned immediately — no duplicate order is created.
The user receives an identical success response to the first attempt.

For status update retries, the client resends `PATCH .../status`. The idempotency
check (`order.status == newStatus`) returns `200 OK` — no duplicate write, no
duplicate event.

**User experience:** the user's order exists and is in the correct state. No
visible error occurs. The gap is that downstream systems (e.g., kitchen display,
delivery tracker) do not receive the Kafka event, so they may not reflect the
new status until reconciled against the DB.

**Production fix:** the Transactional Outbox Pattern writes the event record
atomically with the DB change. A relay process publishes it to Kafka on recovery,
independent of JVM liveness. See ADR-001 for the full production architecture.

---

## Ambiguity decision — cancelling a DELIVERED order

Cancellation of a `DELIVERED` order is **rejected with HTTP 422**.

Rationale: a delivered order has completed its full lifecycle. The `DELIVERED`
state is terminal by domain design — the `OrderStatus` enum defines no valid
outgoing transitions from `DELIVERED`. Allowing cancellation at this stage
would require integration with a payment refund system and a reversal workflow,
both of which are outside the scope of this service. The clean domain boundary
is: once delivered, the order is immutable.

Any attempt to cancel a delivered order returns:
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Cannot transition from DELIVERED to CANCELLED"
}
```

---

## Status idempotency — how repeated identical transitions are handled

If the same status transition arrives multiple times (e.g., the internal calling
service retries up to 3 times after a timeout), the service handles this safely
at the domain level:

1. The service reads the current order status from the DB.
2. If `order.getStatus() == requestedNewStatus`, the order is already in the
   target state — the transition is treated as a no-op.
3. The current order state is returned with `200 OK`.
4. **No DB write occurs** — the `version` column is not incremented.
5. **No Kafka event is published** — the `@TransactionalEventListener` is not
   triggered. Downstream consumers receive exactly one event per genuine
   transition, not one per retry.

This behaviour is independent of the `Idempotency-Key` mechanism used for order
creation — it is a domain-level check, not a key lookup.

---

## What I'd do differently

Given more time or in a production context:

1. **Transactional Outbox Pattern instead of AFTER_COMMIT listener.** Write
   event records to an `outbox` table atomically with the DB change. Use a
   Debezium CDC connector reading the Postgres WAL to publish events to Kafka.
   This provides at-least-once delivery that survives any crash scenario.

2. **Spring Security instead of X-Customer-Id header.** Implement JWT-based
   authentication with a proper identity context. Service-to-service calls
   would use mTLS or an internal service token, decoupled from customer identity.

3. **Rate limiting on order creation.** Bucket4j or a Resilience4j RateLimiter
   to prevent burst abuse per customer.

4. **Keyset (cursor-based) pagination.** Replace OFFSET-based pagination with
   `WHERE created_at < :cursor ORDER BY created_at DESC LIMIT :size` to avoid
   linear scan degradation at high row counts.

5. **Unit and integration test coverage.** Unit tests for `OrderStatus` transition
   logic. Integration tests with Testcontainers (real Postgres + real Kafka) for
   the idempotency race condition, optimistic locking, and Kafka event publishing.

6. **Observability.** Micrometer counters for `status_transitions_total` (by
   from/to status and type: genuine vs replay), Kafka publish failure rate, and
   idempotency hit rate. Distributed tracing via OpenTelemetry with correlation
   IDs propagated through Kafka message headers.

7. **HikariCP tuning.** Set `maximumPoolSize` to `(core_count * 2) + 1` per
   instance rather than the default 10. Scale horizontally rather than increasing
   pool size beyond the Postgres connection ceiling.

---

## Known limitations

| Limitation | Notes |
|------------|-------|
| Kafka events can be lost on JVM crash | At-most-once delivery. Documented in ADR-001. Fix: Transactional Outbox. |
| No real authentication | `X-Customer-Id` header is not verified. Anyone can impersonate any customer. |
| No integration or unit tests | Omitted under time constraints. Correctness validated manually via `api-tests.http`. |
| OFFSET-based pagination | Degrades linearly at high row counts. Acceptable at current scale; keyset pagination required at 1M+ rows per customer. |
| Idempotency keys never expire | No cleanup job implemented. At 1M orders/day, the `idempotency_keys` table grows ~2GB/day. Production fix: scheduled DELETE for keys older than 24 hours. |
| No payment integration | Orders transition to CONFIRMED without any payment verification. |
| X-Customer-Id auth on status updates | Real-world: forward transitions (CONFIRMED, PREPARING, etc.) would be called by internal services using service-to-service auth, not customer identity. |
| No Dockerfile / Docker Compose app service | The app must be started with `./gradlew bootRun` separately; it is not containerised in this submission. |

---