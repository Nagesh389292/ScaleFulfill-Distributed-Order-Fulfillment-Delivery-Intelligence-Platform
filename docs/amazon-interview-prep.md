# ScaleFulfill — Amazon SDE Interview Preparation Guide

This guide packages ScaleFulfill into high-impact portfolio artifacts for Amazon SDE / Senior SDE interviews. Every claim, number, and story is grounded strictly in the **empirically validated Phase 1–7 baseline** (`commit 72d67d8`).

---

## 1. Amazon SDE Resume Bullets (Tailored for Resume / LinkedIn)

Use these quantified, action-oriented bullets directly on your resume:

- **Distributed Systems & Event-Driven Architecture:**
  > *Designed and implemented an event-driven fulfillment platform in Java 21 / Spring Boot, decoupling transactional order intake from inventory, search, and prediction services using Apache Kafka and the Transactional Outbox pattern; sustained 306.4 req/s direct ingress with zero 5xx errors.*

- **Resilience Engineering & Fault Isolation:**
  > *Engineered fault-tolerant microservices with idempotent consumers (Inbox pattern), achieving 100% order availability during downstream Inventory, OpenSearch, and Kafka broker outages with zero dropped orders and automatic outbox flushing (0.17s flush duration).*

- **Mathematical Optimization & Workload Scaling:**
  > *Developed a multi-criteria fulfillment engine evaluating greedy heuristic (<2ms) vs. Google OR-Tools SCIP MILP (26–332ms), reducing warehouse split shipments and dispatch costs by 3–10% ($340–$770/wave); optimized worker thread scaling to achieve 425.4 events/sec.*

- **CQRS & Query Isolation:**
  > *Decoupled read queries from relational write tables via an OpenSearch CQRS search projection; achieved 19ms P50 / 30ms P95 search latency with 133.98ms indexing lag, completely isolating write-path database locks from heavy search traffic.*

- **Distributed Invariant & Observability:**
  > *Built an automated cross-datastore correlation audit validating state consistency across 2,332 orders (0 orphans, 0 missing projections, 0 lag); instrumented full observability using Prometheus and Grafana tracking 9 domain metric families.*

---

## 2. The 2-Minute Project Explanation (Elevator Pitch)

> **Interviewer:** *"Tell me about a project you've worked on recently."*

**Your Answer:**

> "I recently engineered **ScaleFulfill**, a distributed order fulfillment and delivery intelligence platform. Rather than building a generic microservices demo, I designed the project as an **empirical distributed systems study** across seven distinct architectural phases.
>
> I started with an in-process transactional monolith using PostgreSQL and optimistic locking. While it achieved 512 req/s, popular SKUs suffered severe hot-row lock contention. To address this, I decomposed the system into separate Order and Inventory microservices with synchronous HTTP calls. But when I benchmarked that architecture, throughput collapsed by 86.5% down to 68.86 req/s due to network hop latency and cascading thread pool exhaustion.
>
> That empirical failure motivated my transition to an **event-driven architecture**. I implemented the **Transactional Outbox pattern** with Apache Kafka, committing orders and outbox events in a single local database transaction. This decoupled order intake from downstream systems, restoring ingress latency to under 15ms. To protect the database from complex query scans, I introduced a **CQRS search projection with OpenSearch**, delivering sub-30ms search queries.
>
> I then added analytical workloads: an asynchronous **delivery prediction service** with configurable multi-worker scaling, and a **dual-mode wave optimizer** comparing a sub-2ms greedy checkout router against a Google OR-Tools Mixed Integer Linear Programming (MILP) solver that cuts wave fulfillment costs by 3 to 10 percent.
>
> Finally, I built an automated chaos harness and subjected the entire cluster to live component kills—shutting down Inventory, OpenSearch, and the Kafka broker under load. Across all outages, customer orders maintained 100% intake availability with zero data loss. After recovery, an automated cross-database correlation audit verified that all 2,332 persistent orders converged across PostgreSQL, OpenSearch, and the prediction store with zero orphaned records, zero duplicate reservations, and zero Kafka lag."

---

## 3. The 5-Minute Technical Deep Dive (Whiteboard / Architecture Round)

When an interviewer asks you to go deep into the architecture, structure your response around **four pillars**:

```text
1. Transaction Boundaries & The Dual-Write Problem
2. Consumer Idempotency & At-Least-Once Delivery
3. CQRS Read-Path vs Write-Path Separation
4. Failure Recovery & Mathematical State Convergence
```

### Pillar 1: Ingress Durability & The Outbox Pattern
"The most critical requirement of an e-commerce platform is: **never lose a customer order, even if messaging infrastructure fails**. 
If the Order Service attempts to write to PostgreSQL and call `kafkaTemplate.send()` in the same method, you face the dual-write problem. Distributed two-phase commit (2PC/XA) is fragile and introduces heavy coordinator locks.

Instead, I used the **Transactional Outbox pattern**:
- An incoming order executes `BEGIN TRANSACTION` in PostgreSQL (`order_db`).
- It inserts into `orders` and appends an event to `outbox_events` with status `PENDING`.
- Both rows commit atomically in the local ACID transaction.
- An asynchronous background publisher polls pending events and transmits them to Kafka, updating their status to `PUBLISHED`.
- During our Phase 7 chaos test, I killed the Kafka broker while submitting orders. 20 out of 20 orders committed cleanly to PostgreSQL, 20 events buffered in the outbox table, and when Kafka recovered 8.3 seconds later, the publisher flushed the entire buffer in 0.17 seconds with zero dropped orders."

### Pillar 2: Consumer Idempotency & The Inbox Pattern
"Because Kafka guarantees at-least-once delivery, consumers must handle duplicate messages caused by network timeouts or consumer restarts. 
To prevent duplicate SKU deductions, the `inventory-service` implements the **Inbox Pattern**:
- Each incoming message envelope contains a UUID `eventId`.
- Before deducting stock, the consumer checks the `inventory_db.processed_events` table.
- In Phase 7, I injected a duplicate replay experiment by republishing an existing event payload directly to the Kafka topic. The consumer detected the existing key, skipped business processing, and verified that available stock and reservation counts remained unchanged."

### Pillar 3: CQRS Read-Path Isolation (OpenSearch)
"Order history and product lookups require multi-field filtering and full-text search. Running these against PostgreSQL leads to table scans, buffer pool eviction, and contention with checkout transactions.
Using **CQRS**, the Search Service consumes `order.events.created` and asynchronously projects order documents into OpenSearch (average lag: 133.98 ms). Search queries achieve 19ms P50 and 30ms P95. More importantly, when OpenSearch was taken down, order intake continued at over 300 req/s with zero impact."

### Pillar 4: Prediction & Wave Optimization Trade-offs
"For fulfillment optimization, I evaluated the trade-off between speed and optimality:
- **Greedy heuristic:** Runs in under 2ms. Used for real-time customer checkout routing.
- **Google OR-Tools SCIP MILP:** Evaluates multi-center wave dispatch (50–500 orders). In 26–332ms, it globally optimizes split shipments and distances, delivering 3–10% cost savings ($340–$770/wave)."

---

## 4. 18 Amazon SDE Interview Questions & Grounded Answers

### Architectural & System Design Questions

#### Q1: Why did your throughput drop from 512 req/s to 68.86 req/s when moving to microservices in Phase 2?
> **Answer:** "In Phase 1, order creation and inventory deduction happened within a single JVM using in-memory method calls and local database transactions. In Phase 2, decomposing into separate Order and Inventory services introduced HTTP serialization, network transport overhead, and synchronous blocking I/O. Each incoming order held a thread in the Order Service waiting on the Inventory Service HTTP response. This created a cascading thread pool bottleneck in Tomcat, dropping throughput by 86.5% and illustrating the anti-pattern of a distributed monolith."

#### Q2: How does your system guarantee zero lost orders when Kafka crashes?
> **Answer:** "By strictly decoupling the customer order commit from Kafka network availability using the Transactional Outbox pattern. The order is committed to PostgreSQL in the same transaction as an `outbox_events` record with status `PENDING`. If Kafka is unreachable, the outbox publisher encounters connection timeouts and backs off, but the customer order is already durably persisted in PostgreSQL. When Kafka returns, the publisher resumes polling pending events and flushes them to the topic. In Phase 7, we proved this by killing Kafka: 20/20 orders committed during the outage, and all 20 published within 0.17 seconds of recovery."

#### Q3: What happens if the outbox publisher crashes after publishing to Kafka but before updating the outbox status to PUBLISHED?
> **Answer:** "If the publisher crashes before marking the outbox event as `PUBLISHED`, the restarted publisher will re-read that event and publish it again. This produces a duplicate message on Kafka. However, downstream consumers (Inventory, Search, Prediction) implement the Inbox pattern: they record every processed `eventId` in a unique-constrained inbox table. When the duplicate arrives, the consumer detects the existing ID and skips execution, maintaining end-to-end exactly-once business semantics."

#### Q4: Why did you choose OpenSearch instead of adding read replicas to PostgreSQL?
> **Answer:** "Read replicas can scale relational read throughput, but they don't solve the indexing model mismatch. Multi-faceted product search, typo tolerance, and complex filtering on customer order history require inverted indexes and tokenization. In PostgreSQL, complex text searches require GIN indexes and heavy query planning that consume CPU and cache. OpenSearch provides native inverted index lookups in sub-30ms and scales horizontally without imposing query load on the relational database."

#### Q5: How do you handle dead-letter messages when a consumer encounters corrupted data?
> **Answer:** "We configured a Dead Letter Queue (DLQ) topic `order.events.created.DLQ` with Spring Kafka's `DeadLetterPublishingRecoverer` and exponential backoff retry. If a poison-pill payload fails deserialization after 3 retry attempts, it is quarantined to the DLQ with failure metadata headers (exception message, stack trace, timestamp), preventing head-of-line blocking on the main topic partition."

#### Q6: How do you choose between Greedy allocation and Mixed Integer Linear Programming (MILP)?
> **Answer:** "It is a deliberate latency-versus-optimality trade-off based on customer context:
> - Synchronous checkout requires sub-50ms response times. The Greedy algorithm evaluates inventory distance in <2ms, making it ideal for the interactive path.
> - Warehouse wave releases happen in scheduled batches (e.g., every 15 minutes). For 50 to 500 orders, OR-Tools SCIP MILP solves the NP-hard allocation problem in 26–332ms, cutting split shipments and logistics costs by 3–10% ($340–$770/wave), where a 100ms compute delay is completely acceptable."

#### Q7: Why did your lightweight prediction workload perform worse with 8 workers than with 1 worker?
> **Answer:** "For sub-millisecond mathematical tasks (such as our kinematic ETA estimator taking ~0.05ms), the overhead of thread context switching, task queue synchronization (`LinkedBlockingQueue` lock contention), and cache invalidation exceeded the compute duration. 1 worker was fastest at 449.5 evt/s. However, when we evaluated CPU-intensive numerical simulations requiring actual CPU time, 8 workers scaled effectively, increasing throughput by 23.7% (425.4 evt/s) and dropping P95 latency from 68.3ms to 50.5ms."

#### Q8: How did you audit consistency across different storage engines without matching aggregate counts?
> **Answer:** "In a live distributed system, different datastores accumulate historical data across test runs, making raw count equality ($N_{\text{orders}} = N_{\text{inventory}} = N_{\text{search}}$) invalid. Instead, I designed a **correlation-based invariant**: for every persistent `order_id` in PostgreSQL, the audit queries for an exact matching published outbox event, an inventory processing record, a delivery prediction, and an OpenSearch document. Across all 2,332 persistent orders in the cluster, our audit proved 0 orphans, 0 missing records, 0 duplicate allocations, and 0 Kafka lag."

#### Q9: What rate limiting strategy did you implement at the API Gateway?
> **Answer:** "We implemented a Redis-backed Token Bucket filter in Spring Cloud Gateway with a capacity of 20 tokens and a refill rate of 10 tokens/second. When burst traffic exceeded capacity, the gateway admitted exactly the token budget with 201 Created and rejected excess requests immediately with HTTP 429 (Too Many Requests) in sub-80ms, preventing downstream thread starvation and eliminating 5xx server crashes."

#### Q10: How would this architecture scale if order volume grew 100x?
> **Answer:** "The architecture is decoupled at every scale boundary:
> 1. Ingress: Order Service instances scale horizontally behind the API Gateway load balancer.
> 2. Database: PostgreSQL can be sharded by `customer_id` using consistent hashing (`hash(customer_id) % N`) since customer orders are independent.
> 3. Kafka: Increase topic partitions from 3 to 32+, allowing consumer groups across Inventory, Search, and Prediction to scale horizontally across worker nodes.
> 4. OpenSearch: Add data nodes and shard partitions to distribute indexing throughput."

---

### Amazon Leadership Principles (LP) Behavioral Questions

#### Q11: Tell me about a time you made an architectural decision that improved system resilience. (Customer Obsession / Invent & Simplify)
> **STAR Story:**
> - **Situation:** In Phase 2, our microservice architecture used synchronous REST calls from Order Service to Inventory Service.
> - **Task:** I needed to ensure customer order checkout would not fail during downstream inventory hiccups or network latency spikes.
> - **Action:** I measured the synchronous system under load and observed an 86.5% throughput drop and total failure cascade when Inventory crashed. I replaced synchronous REST with the Transactional Outbox pattern on PostgreSQL and Apache Kafka, ensuring the customer transaction commits locally and independent of network messaging availability.
> - **Result:** In Phase 7 chaos testing, when Inventory was completely shut down, 30/30 customer orders were accepted with 100% availability. After Inventory recovered, the backlog drained to zero in 23.56s with zero dropped orders.

#### Q12: Tell me about a time you dove deep into data to solve a technical mystery. (Dive Deep)
> **STAR Story:**
> - **Situation:** In Phase 6, after implementing a multi-threaded worker pool for prediction workloads, adding more worker threads paradoxically degraded throughput on our initial benchmark.
> - **Task:** Identify why 8 worker threads were slower than 1 worker thread.
> - **Action:** I profiled the worker thread execution path. First, I discovered console-lock contention on hot-path `INFO` logging across concurrent threads. Second, I realized that for lightweight sub-millisecond tasks (~0.05ms), queue synchronization locks and thread handoffs cost more than the compute itself. I benchmarked both lightweight and CPU-intensive workloads across 1, 2, 4, and 8 workers.
> - **Result:** With CPU-intensive simulations, 8 workers increased throughput by 23.7% (425.4 evt/s) and reduced P95 latency from 68.3ms to 50.5ms, confirming that thread pool sizing must match task computational intensity.

#### Q13: Tell me about a time you had to make a trade-off between speed and quality. (Bias for Action / Are Right, A Lot)
> **STAR Story:**
> - **Situation:** In designing fulfillment center assignment in Phase 6, real-time customer checkout required sub-50ms latency, but optimal warehouse multi-center assignment is an NP-hard combinatorial problem.
> - **Task:** Decide whether to use an exact mathematical solver or a fast heuristic.
> - **Action:** Instead of forcing a single suboptimal compromise, I implemented a **dual-mode engine**: a Greedy nearest-feasible heuristic for interactive checkouts, and a Google OR-Tools SCIP Mixed Integer Linear Programming (MILP) solver for batch warehouse wave dispatches.
> - **Result:** Checkout responses remained under 2ms, while wave planning executed in 26–332ms, cutting split shipments and fulfillment costs by 3–10% ($340–$770/wave), satisfying both customer latency and operational cost objectives.

#### Q14: Tell me about a time you owned a project end-to-end and ensured its quality. (Ownership)
> **STAR Story:**
> - **Situation:** In Phase 7, initial benchmark reports asserted that `Orders = Inventory = Predictions = Search Documents`, but raw table counts showed 2,332 orders vs 4,341 inventory records and 4,329 search documents.
> - **Task:** Ensure the system's consistency claim was mathematically sound and technically defensible.
> - **Action:** Rather than manipulating counts or ignoring the discrepancy, I recognized that tables accumulated historical test records across phases. I redesigned the audit into a **correlation-based invariant** using `order_id` and `event_id` mapping. I discovered that 9 legacy orders had failed search indexing during a prior ungraceful shutdown test, reconciled them, and re-ran the full cross-database audit.
> - **Result:** The audit verified 100% correlation across all 2,332 orders with 0 orphan orders, 0 missing inventory, 0 missing search docs, 0 duplicate reservations, and 0 Kafka lag.

#### Q15: Tell me about a time you had to challenge a commonly accepted practice. (Have Backbone; Disagree & Commit)
> **STAR Story:**
> - **Situation:** Many microservice tutorials advocate publishing directly to message brokers like Kafka within the business service method.
> - **Task:** Evaluate whether direct publishing met our enterprise durability and zero-data-loss requirements.
> - **Action:** I analyzed the failure modes of direct publishing: if the database transaction commits but the network fails, events are lost; if publishing succeeds but the database rolls back, phantom events are created. I rejected direct publishing and implemented the Transactional Outbox pattern with local database durability.
> - **Result:** When we subjected the system to a complete Kafka broker outage under load, 20/20 orders committed to PostgreSQL with 20 buffered outbox events, and flushed completely in 0.17 seconds upon broker recovery.

#### Q16: How do you handle telemetry and monitoring in high-throughput systems? (Deliver Results)
> **STAR Story:**
> - **Situation:** Operating decoupled asynchronous services creates blind spots; failures downstream in consumer groups are invisible to the ingress gateway.
> - **Task:** Provide end-to-end visibility into consumer lag, processing duration, and error rates without impacting throughput.
> - **Action:** In Phase 5, I instrumented 9 custom domain metric families using Micrometer and configured Prometheus scraping across all 5 services at 15s intervals.
> - **Result:** During fault-injection tests, consumer lag accumulation and gateway rate-limiting rejections were visualized in Grafana in real time, enabling immediate root-cause diagnosis.

#### Q17: Tell me about a time you optimized a system bottleneck. (Deliver Results)
> **STAR Story:**
> - **Situation:** In Phase 4, complex text queries and order history lookups were competing with write transactions on the primary relational database.
> - **Task:** Isolate search queries while maintaining sub-second search freshness.
> - **Action:** I implemented a CQRS search projection using OpenSearch 2.12 asynchronously populated from Kafka.
> - **Result:** Query latency dropped to 19ms P50 and 30ms P95 with an indexing lag of 133.98ms, eliminating database query locks and ensuring order writes were 100% immune to search cluster failures.

#### Q18: What is the most important lesson you learned from building ScaleFulfill? (Self-Reflection / Continuous Learning)
> **Answer:** "The most important lesson is that **architectural complexity must be earned through measurement, not assumed by dogma**. When we moved from a monolith to microservices in Phase 2, our throughput dropped 86.5% because we introduced distributed communication overhead without an asynchronous architecture. Only by measuring bottlenecks, understanding failure coupling, and introducing the Transactional Outbox and CQRS patterns did the distributed system actually outperform and out-survive the monolith. Measurement must always precede architecture."

---

## 5. Live Interview Demo Script (5-Minute Impressive Walkthrough)

If an interviewer asks you for a live terminal demo or code walkthrough, follow this exact sequence:

```powershell
# Step 1: Show cluster health & passing tests
.\mvnw.cmd test -pl fulfillment-core,inventory-service,order-service,api-gateway,search-service,prediction-service
# Point out: 48/48 tests passing across all 6 modules

# Step 2: Run the Cross-Datastore Correlation Audit
python scripts/verify_correlation_audit.py
# Point out: 2,332 persistent orders verified across PostgreSQL, Inventory, Search & Prediction
# Show: 0 orphan orders, 0 missing inventory, 0 missing search docs, 0 lag, 0 pending outbox

# Step 3: Demonstrate Idempotency & Duplicate Replay
# Highlight Scenario 5 in scripts/benchmark_phase7_reliability.py:
# Show how re-publishing an existing event payload results in 0 stock deduction and 0 duplicate inbox entries

# Step 4: Show the Outbox & Kafka decoupling
# Review ADR-013 and Section 5 of docs/benchmarks/phase7-reliability-engineering.md:
# Explain how Kafka was stopped for 8.3s, 20 orders committed safely to PostgreSQL, and flushed in 0.17s
```
