# Phase 2 Distributed Synchronous Microservices — Failure Mode & Resilience Report

**Date:** 2026-09-27  
**Test Suite:** `tests/test_phase2_failure_scenarios.py` & `tests/test_phase2_resilience.py`  
**Architecture:** Client → API Gateway (8080) → Order Service (8081) → Inventory Service (8082)

---

## 1. Executive Summary

Phase 2 verified that decomposing a monolithic system into separate services with separate databases eliminates shared database contention, but introduces **distributed network boundaries, cascading failure risks, and inter-service synchronous latency**.

To address these failure modes before transitioning to asynchronous messaging in Phase 3, we implemented and empirically verified:
1. **Edge Burst Protection:** Redis-backed token bucket rate limiting on the API Gateway (HTTP 429).
2. **Distributed Rate Limiting Across Nodes:** Shared Redis keys (`ratelimit:tokens:<key>`, `ratelimit:ts:<key>`) shared between distinct Gateway instances.
3. **Resilience4j Fault Tolerance:** Circuit Breaker, Timeout, and Retry with exponential backoff on `OrderService -> InventoryService`.
4. **Structured Error Propagation:** Downstream business errors (4xx) and system outages (503) cleanly handled without leaking stack traces or unhandled 500s.

---

## 2. Empirical Verification of Scenarios

### Scenario A — Normal Distributed Flow (Healthy Services)
```text
Client ──► API Gateway (:8080) ──► Order Service (:8081) ──► Inventory Service (:8082)
              [Injected UUID]         [Reserves Stock]            [Deducts Available]
```
- **Test Command:** `POST /api/v1/orders` via Gateway
- **Observed Response:** `HTTP 201 Created`
- **Payload:** `orderId: ORD-3F8250BE`, `status: RESERVED`, `assignedFcId: FC-BLR-01`
- **Header:** `X-Correlation-Id: 6205e201-94da-43e9-b279-82a19eace0f5` (verified end-to-end)

---

### Scenario B — Downstream Inventory Service Outage & Circuit Breaker Fast-Failure
```text
Client ──► API Gateway (:8080) ──► Order Service (:8081) ──► [X] Inventory Service DOWN
                                         │
                                   Resilience4j
                                  (Retries Exhausted)
                                         ▼
                                   Circuit OPEN
                                         ▼
                             HTTP 503 Service Unavailable
                              (Retry-After: 5s, No 500/NPE)
```
- **Action:** Process for `inventory-service` (:8082) killed.
- **Initial Request:** Order Service retries downstream, reaches retry limit, and calls `reserveInventoryFallback(...)`.
- **Observed Response:** `HTTP 503 SERVICE_UNAVAILABLE`
  ```json
  {
    "timestamp": "2026-09-27T13:44:50.123Z",
    "status": 503,
    "error": "DOWNSTREAM_SERVICE_UNAVAILABLE",
    "message": "Inventory service is temporarily unavailable: Inventory service is currently unavailable or circuit breaker is OPEN. Fast-failing.",
    "path": "/api/v1/orders"
  }
  ```
- **Headers:** `Retry-After: 5`
- **Subsequent Requests (Circuit Breaker OPEN):**
  - Call 1 Latency: `94.99 ms` (Fast-fail)
  - Call 2 Latency: `63.36 ms` (Fast-fail)
  - Call 3 Latency: `53.71 ms` (Fast-fail)
- **Key Verification:** Zero `NullPointerException`, zero unhandled `500 Internal Server Error`, zero raw connection stack traces leaked to client. Upstream Order Service thread pool remained completely unblocked.

---

### Scenario C — Service Recovery & Circuit Breaker Reset
```text
Inventory Restarts ──► 3s Open Duration Elapsed ──► Trial Call (HALF-OPEN) ──► Circuit CLOSED
```
- **Action:** `inventory-service` restarted on port 8082.
- **Circuit Transition:** After `wait-duration-in-open-state: 2500ms`, circuit moved to `HALF-OPEN`.
- **Trial Call:** Request to `POST /api/v1/orders` succeeded with `HTTP 201 Created` (`ORD-C9C9286A`).
- **State:** Circuit Breaker returned to `CLOSED`. Normal traffic restored automatically without manual operator intervention.

---

## 3. Distributed Redis Rate Limiting Verification

Verified via `DistributedRedisRateLimiterTest`:
- **Scenario:** Two distinct Gateway instances (`gatewayInstanceA` and `gatewayInstanceB`) sharing the exact same Redis keys.
- **Test:** Instance A handled 3 requests, Instance B handled 2 requests (Capacity = 5).
- **Result:** Requests 6 and 7 were rejected with `false` across both instances.
- **Redis Verification:** State confirmed in Redis keys:
  - `ratelimit:tokens:<key>`: decremented to `0.0`
  - `ratelimit:ts:<key>`: recorded atomic timestamp
- **Takeaway:** Rate limiting is genuinely distributed across independent gateway nodes rather than isolated in local JVM memory.

---

## 4. Phase 1 vs Phase 2 Benchmark Comparison

| Metric | Phase 1 Monolith | Phase 2 Distributed Synchronous | Architectural Explanation |
| :--- | :--- | :--- | :--- |
| **Topology** | Single In-Memory Process | Gateway → Order Svc → Inventory Svc | True distributed boundaries with independent deployments |
| **Database Ownership** | Shared PostgreSQL | Isolated `order_db` & `inventory_db` | Eliminates cross-domain schema coupling and shared DB pool starvation |
| **Tier 1 (P50 Latency)** | **32.0 ms** | **57.09 ms** | **+78.4% latency overhead** due to network hops (Gateway Netty + HTTP JSON serialization) |
| **Tier 2 (P95 Latency)** | **46.9 ms** | **406.89 ms** | Compounding thread wait times across two downstream network boundaries |
| **Tier 3 (Throughput)** | **512.6 req/s** | **68.86 req/s** | **7.4x throughput reduction** caused by synchronous blocking threads across 3 network tiers |
| **Edge Protection** | None | Token Bucket Rate Limiting (HTTP 429) | Prevents upstream burst DDoS from reaching backend services |
| **Downstream Outage** | Process crash | Resilience4j Circuit Breaker (503 Fast-Fail) | Order parks in `PENDING_INVENTORY_VERIFICATION` to protect thread pool |

---

## 5. The Engineering Rationale for Phase 3 (Apache Kafka)

The benchmark results and failure scenarios provide the **exact, empirical reason to move to Phase 3**:

1. **Synchronous Call Bottleneck:** Synchronous HTTP calls force the Order Service to block worker threads while awaiting Inventory responses. Under concurrency, throughput dropped from 512 req/s to 68.8 req/s.
2. **Availability Coupling:** If Inventory Service experiences transient latency or is temporarily down, orders cannot be synchronously confirmed as `RESERVED`.
3. **The Solution (Phase 3):**
   - **Transactional Outbox Pattern:** Order Service writes the order to `order_db` and an outbox event in the SAME local ACID transaction (< 5 ms).
   - **Kafka Event Stream:** Debezium / Outbox Poller publishes `OrderCreatedEvent` to Kafka.
   - **Asynchronous Inventory Allocation:** Inventory Service consumes events at its own pace, reserving inventory idempotently.
   - **Dead Letter Queue (DLQ):** Unresolvable failures are routed to DLQ without blocking the system.
