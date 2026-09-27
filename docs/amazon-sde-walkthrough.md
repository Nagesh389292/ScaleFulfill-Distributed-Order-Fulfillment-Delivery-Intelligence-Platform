# ScaleFulfill — Amazon SDE Technical Walkthrough & Presentation Guide

> **"I started with a transactional fulfillment system, measured its bottlenecks, introduced synchronous service boundaries, measured the resulting failure coupling, replaced them with durable asynchronous events, added CQRS and observability, introduced prediction and optimization workloads, and finally subjected the complete system to failure and recovery experiments."**
> — *The Engineering Narrative of ScaleFulfill*

---

## 1. The 30-Second Elevator Pitch

ScaleFulfill is not a technology showcase—it is an **empirically driven distributed systems journey**. Over seven disciplined engineering phases, each architectural decision was motivated by a measured failure mode or throughput bottleneck in the preceding architecture:

```text
Phase 1: Monolith (512 req/s) 
  └─► Bottleneck: Hot-row database lock contention & shared failure domain
Phase 2: Synchronous Microservices (68.86 req/s)
  └─► Bottleneck: 86.5% throughput collapse due to HTTP network latency & cascading failure coupling
Phase 3: Event-Driven Kafka + Transactional Outbox (<15ms ingress)
  └─► Solution: Decoupled producer from consumers; atomic database outbox durability
Phase 4: CQRS & OpenSearch Projections (19ms P50 search)
  └─► Solution: Inverted indexing isolated search queries from transactional write tables
Phase 5: Full Observability Mesh (5/5 targets UP, 9 metric families)
  └─► Solution: Real-time telemetry to detect consumer lag, error spikes, and circuit trips
Phase 6: Distributed Predictions & Wave Optimization (425 evt/s, 3-10% cost savings)
  └─► Solution: Worker pool scaling + dual-mode optimizer (Greedy <2ms vs OR-Tools MILP)
Phase 7: End-to-End Reliability & Chaos Validation (306.4 req/s peak)
  └─► Validation: 0 dropped orders during Inventory, Search, and Kafka broker outages;
                  0 duplicate allocations; 100% correlation invariant across 2,332 orders.
```

---

## 2. Phase-by-Phase Technical Breakdown

### Phase 1: In-Process Transactional Core
* **The Goal:** Establish a correct baseline for inventory reservation and order intake using ACID transactions and optimistic locking (`@Version`).
* **Measured Result:** Sustained **512.0 req/s** with a P95 latency of **18.0 ms**.
* **Observed Bottleneck:** Under high concurrent demand on popular SKUs (flash sales), thread contention on individual PostgreSQL rows triggered optimistic lock retry storms and database connection pool starvation. Furthermore, placing all domains in a single runtime meant any inventory bug could crash order intake.

### Phase 2: Synchronous Microservice Decomposition
* **The Goal:** Separate Order and Inventory domains into distinct microservices, independent databases (`order_db`, `inventory_db`), and a Spring Cloud Gateway with Redis token-bucket rate limiting and Resilience4j circuit breakers.
* **Measured Result:** Peak throughput collapsed by **86.5%**, dropping to **68.86 req/s** with P95 latency increasing to **75.3 ms**.
* **The Critical Finding:** Synchronous HTTP RPC introduced network latency hops, serialization overhead, and thread blocking. More dangerously, it created **tight runtime coupling**: when the Inventory Service slowed or failed, Order Service threads blocked waiting for responses, quickly exhausting the Tomcat thread pool and cascading into total system unavailability.

### Phase 3: Asynchronous Event Fabric & The Transactional Outbox
* **The Goal:** Break the synchronous coupling while guaranteeing transactional durability and idempotency.
* **The Architectural Pattern:**
  ```text
  Customer Request
         ↓
  Order Service
         ↓
  PostgreSQL: BEGIN TRANSACTION
    ├── INSERT INTO orders (...)
    └── INSERT INTO outbox_events (..., status='PENDING')
  COMMIT;
         ↓
  Asynchronous Outbox Publisher (500ms poll)
         ↓
  Kafka Topic: order.events.created
         ↓
  Inventory Consumer (Inbox Pattern: processed_events)
  ```
* **Measured Result:** Direct order intake latency dropped back to **< 15.0 ms**, with the Outbox Publisher sustaining a burst publishing rate of **471.4 events/sec**.
* **Distributed Resilience:** The customer checkout transaction no longer depended on Kafka or Inventory availability. Poison-pill messages were automatically isolated to a Dead Letter Queue (DLQ), and network duplicates were dropped via the Inbox table (`processed_events`).

### Phase 4: CQRS Read-Path Isolation with OpenSearch
* **The Goal:** Isolate customer order history search and catalog lookups from the high-throughput transactional database.
* **Implementation:** OpenSearch 2.12 inverted index populated asynchronously by a dedicated `search-service` consuming Kafka events (`order.events.created`).
* **Measured Result:** Search queries responded in **19.0 ms P50 / 30.0 ms P95**, with an end-to-end indexing lag of **133.98 ms**.
* **Architectural Advantage:** When OpenSearch was deliberately taken offline, order creation continued with **zero degradation**. Write-path operations remained completely shielded from read-path outages.

### Phase 5: Full Observability Mesh
* **The Goal:** Real-time visibility into distributed bottlenecks, consumer lag, and error rates across all decoupled services.
* **Implementation:** Prometheus scraping all microservices at 15s intervals via Micrometer metrics; Grafana dashboards tracking 9 custom domain metric families.
* **Empirical Validation:** Demonstrated that during deliberate service failure injections, Kafka consumer lag accumulation, gateway rate-limiting rejections, and outbox buffer depth were detected and visualized within seconds.

### Phase 6: Distributed Predictions & Wave Optimization
* **The Goal:** Integrate compute-intensive analytical and operational research workloads without stalling the event pipeline.
* **Component 1 — Worker Pool Scaling:**
  - Evaluated thread scaling across 1, 2, 4, and 8 workers.
  - *Key Finding:* For CPU-intensive simulations, 8 workers increased throughput by **+23.7%** (425.4 evt/s, P95 dropped from 68.3ms to 50.5ms). For sub-millisecond tasks, 1 worker was fastest (449.5 evt/s) because thread coordination and queue-lock overhead outweighed the computation.
* **Component 2 — Dual-Mode Wave Optimizer:**
  - **Greedy Nearest-Feasible:** Solves single-order checkout routing in **< 2 ms** (ideal for synchronous customer feedback).
  - **Google OR-Tools SCIP MILP:** Solves warehouse wave fulfillment batches in **26–332 ms**, reducing multi-center split shipments and shipping cost by **3–10%** ($340–$770 saved per wave).

### Phase 7: End-to-End Reliability, Load & Failure Engineering
* **The Goal:** Subject the integrated architecture to live load ramp-up, partial infrastructure kills, duplicate message delivery, and mathematical consistency validation.
* **Empirical Findings:**
  1. **Sustained Direct Ingress:** *In the Phase 7 benchmark environment, direct order ingress reached a measured peak of 306.4 req/s* with 100% 201 Created and zero impact on concurrent search queries.
  2. **Inventory Service Outage:** 30/30 orders accepted during outage; after restart, Kafka consumer lag drained to 0 in 23.56s with 0 lost events.
  3. **OpenSearch Outage:** 30/30 orders accepted during search cluster downtime; consumer drained backlog at 38.6 events/sec upon recovery.
  4. **Kafka Broker Outage:** 20/20 orders committed durably to PostgreSQL; 20 events buffered in `outbox_events` as `PENDING`; Kafka restarted in 8.33s; outbox publisher flushed the buffer in **0.17 seconds**.
  5. **Consumer Idempotency:** Duplicate event re-delivery resulted in **0 double-allocations** and **0 stock drift**.
  6. **Correlation Invariant Audit:** Verified across all 2,332 persistent orders:
     $$\text{orphan orders} = 0, \quad \text{missing inventory} = 0, \quad \text{missing predictions} = 0,$$
     $$\text{missing search docs} = 0, \quad \text{duplicate inventory} = 0, \quad \text{Kafka lag} = 0, \quad \text{pending outbox} = 0.$$

---

## 3. Curated Interview Benchmark Table (The Decisive 10 Numbers)

| # | Architectural Milestone / Decision | Measured Metric (Benchmark Environment) | Architectural Trade-off / Technical Significance |
|---|---|:---:|---|
| **1** | **Phase 1: In-Process Monolith** | **512.0 req/s** (P95: 18.0 ms) | High raw throughput via ACID transactions + row locks, but creates a single blast radius and severe hot-SKU lock contention under concurrency. |
| **2** | **Phase 2: Synchronous Microservices** | **68.86 req/s** (P95: 75.3 ms) | **86.5% throughput collapse**. Synchronous HTTP coupling introduced network hops, cascading thread pool exhaustion, and vulnerability to downstream outages. |
| **3** | **Phase 3: Event-Driven Outbox Ingress** | **< 15.0 ms** order ingress (471.4 evt/s outbox burst) | Decoupled order intake from downstream availability. PostgreSQL Transactional Outbox provides local durability without synchronous broker dependency. |
| **4** | **Phase 4: CQRS Read-Path Isolation** | **19.0 ms P50 / 30.0 ms P95** (Indexing lag: 133.98 ms) | Inverted search indexing decoupled query workloads from the relational write path, keeping read and write latency physically isolated. |
| **5** | **Phase 5: Full Observability Telemetry** | **5/5 scrape targets UP** (9 custom metric families) | Provided real-time visibility into consumer lag, errors, and rate limiting with sub-15s scrape resolution while maintaining the measured application workload. |
| **6** | **Phase 6: Parallel Worker Scaling** | **425.4 evt/s** (8 workers vs 344.0 evt/s 1 worker) | +23.7% throughput and P95 latency dropped from 68.3ms to 50.5ms for CPU-intensive simulations. Proved sub-ms tasks should stay on 1 worker to avoid queue-lock overhead. |
| **7** | **Phase 6: Mathematical Optimization** | **Greedy: < 2 ms** vs **MILP: 26–332 ms** (3–10% savings) | Greedy solves real-time checkout routing instantly; OR-Tools SCIP MILP solves warehouse wave batches saving $340–$770/wave at the cost of 26–332ms solve time. |
| **8** | **Phase 7: Sustained Direct Order Ingress** | **306.4 req/s** (100% 201 Created, P95: 117.7 ms) | *In the Phase 7 benchmark environment, direct order ingress reached a measured peak of 306.4 req/s* with zero 5xx errors and sub-35ms concurrent search response. |
| **9** | **Phase 7: Fault Isolation Under Outages** | **30/30 orders accepted** during Inventory kill<br>**20/20 committed** during Kafka outage | Complete ingress resilience. When Kafka was killed, Outbox buffered 20 PENDING events and flushed in 0.17s upon recovery (Kafka RTO: 8.33s). |
| **10**| **Phase 7: Invariant Consistency & Idempotency** | **0 orphan, 0 missing, 0 drift** across 2,332 orders | Replayed duplicates dropped by Inbox table with 0 duplicate reservations. Correlation invariant proven across PostgreSQL, Inventory, Search, and Prediction Store. |

---

## 4. Key Amazon Leadership Principles (LPs) Grounding

### Customer Obsession
* *"A customer ordering an item during a flash sale does not care if the search cluster or analytics engine is experiencing an outage."*
* By establishing the Transactional Outbox pattern, customer order placement was 100% protected against downstream failures. During simulated kills of Inventory, OpenSearch, and Kafka, customer orders were committed safely with zero dropped requests.

### Ownership
* Rather than declaring Phase 7 "done" when the code compiled, I wrote an automated cross-database audit script that validated state across PostgreSQL, Kafka, OpenSearch, and the in-memory prediction store. When raw counts diverged due to historical test runs, I redefined and enforced an exact correlation-based invariant to prove zero data loss.

### Invent and Simplify
* Instead of introducing complex, fragile distributed two-phase commit (2PC/XA) protocols across microservices, I leveraged the simple, robust Transactional Outbox pattern paired with consumer Inbox deduplication. This gave the platform guaranteed eventual consistency and at-least-once delivery with standard local ACID transactions.

### Are Right, A Lot
* When decomposing the monolith in Phase 2, initial intuition might have suggested simple REST calls between microservices. When the benchmark revealed an **86.5% collapse in throughput**, I recognized that synchronous coupling in microservices creates an anti-pattern (a distributed monolith) and pivoted decisively to an event-driven architecture with Kafka.

### Dive Deep
* When multi-threaded prediction workers initially showed counter-intuitive scaling in Phase 6, I profiled the JVM execution path, identified console lock contention on hot-path logging, and discovered that task queue coordination costs dominated sub-millisecond computations. Re-benchmarking with CPU-intensive workloads demonstrated the true scaling threshold.

---

## 5. System Design Interview Q&A (Amazon SDE Defense)

### Q1: Why didn't you just publish directly to Kafka inside the Order Service checkout method?
> **Answer:** "If you publish directly to Kafka inside the order method, you face the dual-write problem. If the database transaction commits but the Kafka publish fails (network glitch, broker timeout), the order exists in the DB but no downstream service ever knows about it. Conversely, if you publish to Kafka first and the database transaction rolls back, you've reserved inventory for an order that never existed. Distributed 2PC transactions are notorious for poor performance and locking. The **Transactional Outbox pattern** solves this elegantly: the order and the outbox event commit in the same local ACID database transaction. Durability is anchored in PostgreSQL; publication to Kafka is asynchronous."

### Q2: What happens if Kafka delivers the same order message twice?
> **Answer:** "Kafka provides at-least-once delivery semantics by default. A network timeout on offset commit can cause a re-delivery. To prevent double allocation, our consumers implement the **Inbox Pattern**. The `inventory_db` maintains a `processed_events` table with `event_id` as the primary key. Before deducting stock, the consumer checks if `event_id` has already been recorded. In our Phase 7 duplicate injection experiment, we re-published an existing event directly to Kafka. The consumer identified the duplicate, logged the bypass, and left available stock and reservation counts completely unchanged."

### Q3: Why separate OpenSearch into a CQRS projection instead of just indexing Postgres?
> **Answer:** "Full-text search, facet aggregations, and multi-field filtering across millions of orders require expensive inverted index scans and JSON text searches. Running those queries on the transactional PostgreSQL database contends with row locks, spikes memory in `shared_buffers`, and degrades checkout write throughput. In Phase 4, we decoupled search into OpenSearch via Kafka. During our Phase 7 load test, concurrent OpenSearch queries responded in sub-35ms while the Order Service was ingesting over 300 orders/sec, with zero latency cross-talk."

### Q4: When do you use the Greedy Optimizer vs the Mixed Integer Linear Programming (MILP) solver?
> **Answer:** "It comes down to latency versus optimality trade-offs:
> - **Greedy Nearest-Feasible (< 2 ms):** Ideal for real-time customer checkout. When a customer clicks 'Place Order', they need immediate feedback. Greedy finds a valid fulfillment center instantly.
> - **Google OR-Tools SCIP MILP (26–332 ms):** Ideal for scheduled warehouse wave releases (e.g., dispatching batches of 50–500 orders every 15 minutes). The solver globally minimizes split shipments and shipping distance across multi-center inventory, saving 3–10% in fulfillment costs ($340–$770/wave) where a 100ms calculation delay is completely imperceptible."

### Q5: How do you verify that your distributed system is consistent?
> **Answer:** "In Phase 7, we moved away from naive aggregate table counts, which can be distorted by historical records. Instead, we enforce a **correlation-based invariant**: for every persistent `order_id` in PostgreSQL, there must exist exactly one published outbox event, one inventory processing record, one computed prediction, and one OpenSearch document, while consumer group lag is zero. Our live automated audit verified this correlation across all 2,332 orders in the system, proving zero orphaned orders, zero missing projections, and zero drift."
