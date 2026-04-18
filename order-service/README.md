# Order Processing Service

## How to run
```bash
# 1. Start all infra (Postgres + Kafka)
docker-compose up -d

# 2. Build/Run the Spring Boot app
./gradlew clean build
./gradlew bootRun

# 3. App available at http://localhost:8080
```
## How to test

# Create Order

Use POST /api/v1/orders with headers:
Idempotency-Key
X-Customer-Id

Example (PowerShell):
```bash
Invoke-RestMethod -Method POST `
-Uri "http://localhost:8080/api/v1/orders" `
-Headers @{
  "Idempotency-Key" = "test-001"
  "X-Customer-Id" = "11111111-1111-1111-1111-111111111111"
} `
-Body (@{
  items = @(
    @{
      menuItemId = "22222222-2222-2222-2222-222222222222"
      name = "Burger"
      quantity = 2
      unitPrice = 100
    }
  )
} | ConvertTo-Json -Depth 5) `
-ContentType "application/json"
```

# Idempotency

Repeat the same request with the same Idempotency-Key.

Expected:

Same order ID
No duplicate order created

# Get Order

GET /api/v1/orders/{ORDER_ID} with header:
X-Customer-Id

# Authorization

Use a different X-Customer-Id.

Expected:
403 Forbidden

# Status Update

PATCH /api/v1/orders/{ORDER_ID}/status

Example:

```bash
Invoke-RestMethod -Method PATCH `
-Uri "http://localhost:8080/api/v1/orders/{ORDER_ID}/status" `
-Headers @{
  "X-Customer-Id" = "11111111-1111-1111-1111-111111111111"
} `
-Body (@{ newStatus = "CONFIRMED" } | ConvertTo-Json) `
-ContentType "application/json"
```

# Invalid Transition

Try invalid transition (e.g., PENDING → DELIVERED).

Expected:
422 Unprocessable Entity


# Concurrency Testing

Concurrency was tested using parallel PowerShell jobs:
```bash
Start-Job {
    Invoke-RestMethod -Method PATCH `
    -Uri "http://localhost:8080/api/v1/orders/{ORDER_ID}/status" `
    -Headers @{ "X-Customer-Id" = "11111111-1111-1111-1111-111111111111" } `
    -Body (@{ newStatus = "CONFIRMED" } | ConvertTo-Json) `
    -ContentType "application/json"
} ; Start-Job {
    Invoke-RestMethod -Method PATCH `
    -Uri "http://localhost:8080/api/v1/orders/{ORDER_ID}/status" `
    -Headers @{ "X-Customer-Id" = "11111111-1111-1111-1111-111111111111" } `
    -Body (@{ newStatus = "CANCELLED" } | ConvertTo-Json) `
    -ContentType "application/json"
}
```

Then:
```bash
Get-Job
Receive-Job -Id <jobId>
```

Expected:

One request succeeds (200)
Other request fails with either:
409 Conflict (optimistic locking)
OR 422 (invalid state transition)

Note:
Due to limitations of manual tools, true concurrency conflicts may not always reproduce.
Optimistic locking guarantees correctness under real concurrent load.

# Kafka Verification

Kafka integration was verified by triggering a status update and checking logs.

Expected logs:

Producer:
Order... transitioned...
Consumer:
[NOTIFICATION] Order ... status changed
Notify: ...

This confirms end-to-end flow:
API → DB → Kafka → Consumer

## Design decisions

### Idempotency
Idempotency-Key is stored atomically with the order in the same
DB transaction. On duplicate request, the stored response body is
returned directly. This prevents double-charges or duplicate orders
even if the client retries due to a timeout.

### Authorization
The service uses the X-Customer-Id header as a simplified auth context.
For all read and write operations, ownership is enforced at the service layer.
If a customer attempts to access or modify another customer's order,
the request is rejected with HTTP 403 Forbidden.

Although the assignment specifies this requirement for retrieval and
cancellation, I applied it consistently to all operations to maintain
secure-by-default behavior. In a real system, status updates would be
restricted to internal services via service-to-service authentication.

### Concurrency (optimistic locking)
Orders use @Version for JPA optimistic locking. If two concurrent
requests try to update the same order, only one succeeds. The other
receives a 409 Conflict response. Chosen over pessimistic (SELECT
FOR UPDATE) because status updates are infrequent and short-lived;
lock contention would be rare and optimistic is more scalable.

### Kafka consistency
DB write and Kafka publish use @TransactionalEventListener
(AFTER_COMMIT). The event fires only after DB commit, preventing
phantom events on rollback. Trade-off: a crash between commit and
publish loses the event. Full solution is Transactional Outbox
(documented in ADR-001).

### Status idempotency (repeated transitions)
If the same status transition arrives multiple times (e.g., internal
service retries), we check if order.status == newStatus BEFORE
attempting to transition. If already in the target state, we return
200 without re-publishing the Kafka event.

## Failure analysis
If the service crashes after the DB write succeeds but before the
Kafka publish fires, the order exists in PENDING status in the
database but no event was published to Kafka.

When the client retries with the same Idempotency-Key, the key is
found in the idempotency_keys table and the stored 201 response is
returned immediately — no duplicate order is created. The user sees
a successful response identical to the first.

The system is left in an inconsistent state where the database reflects
the new order, but no corresponding event exists in Kafka. This can lead
to missed downstream processing (e.g., notifications or delivery workflows).

In this implementation, this risk is partially mitigated because Kafka
events are only emitted on status changes, not on initial creation.
However, this still represents a consistency gap.

In a production system, this would be addressed using the Transactional
Outbox Pattern, ensuring that the event is durably stored and eventually
published even if the service crashes.

## Ambiguity decision — cancelling a DELIVERED order
I chose to **reject cancellation of DELIVERED orders** with HTTP 422
and a clear error message: "Cannot cancel an order that has already
been delivered." Rationale: a delivered order has completed its full
lifecycle. Allowing cancellation of a delivered order would require
refund/reversal logic with the payment system, which is out of scope
for this service. The clean domain boundary is: once delivered,
the order is immutable.

## What I'd do differently
- Implement Transactional Outbox instead of AFTER_COMMIT listener
- Add Spring Security instead of X-Customer-Id header
- Add rate limiting on order creation
- Use database connection pooling (HikariCP tuning)
- Add distributed tracing (OpenTelemetry)

## Known limitations
- Kafka publish can be lost on app crash (see ADR-001)
- No real payment integration — orders go directly to CONFIRMED
- X-Customer-Id header is not authenticated
- No test coverage included (time constraint)