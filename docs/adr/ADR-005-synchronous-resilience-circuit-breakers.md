# ADR-005: Synchronous Service Resilience via Resilience4j Circuit Breakers and Fallbacks

## Status
Accepted

## Context
In our decomposed architecture, `order-service` synchronously invokes `inventory-service` over HTTP during order creation to reserve stock. Synchronous distributed communication is inherently fragile:
- **Cascading Failures:** If `inventory-service` crashes or experiences GC pauses, `order-service` worker threads block waiting for HTTP responses, exhausting its Tomcat thread pool and bringing down order ingress.
- **Thundering Herd:** Once `inventory-service` restarts, thousands of queued requests can immediately overwhelm it.
- **Brittle Availability:** A 1% packet loss or transient blip causes orders to fail unnecessarily unless intelligent retries with backoff are applied.

## Decision
We integrate **Resilience4j** into the `InventoryClient` in `order-service` with three coordinated defensive layers:
1. **Timeouts (TimeLimiter):** Enforces a strict 2000ms deadline on inter-service HTTP calls.
2. **Retries with Exponential Backoff:** Automatically retries transient network errors (up to 3 attempts, starting at 200ms with a 2x multiplier). Business errors (such as 409 Conflict for insufficient stock) bypass retries immediately.
3. **Circuit Breaker:**
   - Tracks a sliding window of 10 requests.
   - If failure rate exceeds 50%, the circuit transitions to `OPEN`.
   - In `OPEN` state, all incoming calls fail fast immediately without making network calls to `inventory-service`, saving resources.
   - After a wait duration of 5000ms, the circuit transitions to `HALF-OPEN`, admitting 3 trial requests before re-closing or re-opening.
4. **Resilient State Degradation (Fallback):**
   - When downstream failure or circuit-open occurs, `order-service` does NOT crash. Instead, the order is safely persisted into `order_db` with state `PENDING_INVENTORY_VERIFICATION`.
   - The customer is notified with HTTP 503 / 202 indicating temporary verification delay, preventing order loss.

## Consequences
### Positive
- Prevents thread exhaustion and protects upstream availability during downstream outages.
- Fast-fails in ~0.5ms when the circuit is open instead of hanging for seconds.
- Empirically demonstrates fault tolerance and graceful degradation in distributed systems.

### Negative / Trade-offs
- Synchronous calls still tie thread lifecycles together during normal operation.
- Orders in `PENDING_INVENTORY_VERIFICATION` require asynchronous reconciliation or message queuing, establishing the clear necessity for Phase 3's Kafka event fabric.
