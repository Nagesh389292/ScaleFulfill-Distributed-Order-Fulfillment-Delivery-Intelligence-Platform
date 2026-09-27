# ScaleFulfill — Distributed Order Fulfillment & Delivery Intelligence Platform

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.7-blue.svg)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7.2-red.svg)](https://redis.io/)
[![OpenSearch](https://img.shields.io/badge/OpenSearch-2.13-teal.svg)](https://opensearch.org/)
[![Three.js](https://img.shields.io/badge/Three.js-3D%20WebGL-black.svg)](https://threejs.org/)
[![Playwright](https://img.shields.io/badge/Playwright-6%2F6%20Passing-green.svg)](https://playwright.dev/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-blue.svg)](https://www.docker.com/)

A production-grade, event-driven distributed e-commerce fulfillment platform engineered to handle high-concurrency order ingestion, partitioned relational storage, dynamic multi-center inventory allocation, mathematical wave optimization, distributed ETA predictions, and comprehensive fault tolerance.

> [!NOTE]
> **Workload Origin & Data Note**
> ScaleFulfill does not use Amazon's proprietary data. The platform uses generated test workloads to exercise concurrency, event processing, search, prediction, optimization, and failure-recovery behaviour. Performance figures in this repository are measurements from those controlled workloads and should not be interpreted as production benchmarks.

---

## 🎯 Core Engineering Problems Solved

ScaleFulfill was architected, benchmarked, and verified across 7 distinct engineering phases to solve foundational distributed systems, scalability, and consistency challenges:

### 1. The Distributed Transaction Dual-Write Problem
* **The Failure Mode**: In a microservice architecture, updating a relational database (e.g. creating an order) while publishing an event to a message broker (e.g. Kafka) cannot be coordinated with standard 2PC without severe latency and coordinator lockups. If the broker is unreachable or crashes mid-flight, the database write commits but the event is lost forever (inconsistent state).
* **What We Solved**: Implemented the **Transactional Outbox Pattern** in PostgreSQL (`order_db.outbox_events`) within a single ACID transaction boundary. When an order is created, both the order and the outbox event commit atomically. A scheduled poller flushes outbox events to Kafka asynchronously. Even if Kafka dies completely, order ingestion succeeds with zero data loss; events buffer locally and flush automatically upon broker recovery (verified: 20/20 orders buffered and flushed in 0.17s upon Kafka recovery).

### 2. Synchronous Coupling & Cascading Latency Collapse
* **The Failure Mode**: A synchronous HTTP microservice design (Phase 2) suffered an **86.5% throughput collapse** (dropping from 512 req/s to 68.86 req/s). Synchronous REST hops caused cascading thread pool exhaustion across services; any downstream latency immediately blocked upstream gateway threads.
* **What We Solved**: Transformed the architecture into an **Asynchronous Event Fabric** using Apache Kafka. Ingress latency dropped to **<15ms** by confining the synchronous path to local PostgreSQL outbox writes. Inventory deduction, search indexing, and delivery ETA predictions are handled asynchronously across dedicated consumer groups, completely decoupling write throughput from downstream processing speeds.

### 3. At-Least-Once Delivery & Duplicate State Corruption
* **The Failure Mode**: Apache Kafka guarantees at-least-once delivery. Network retries, consumer rebalancing, or broker failover inevitably cause duplicate message delivery. In inventory management, processing a duplicated order event causes double stock deductions and ghost shortages.
* **What We Solved**: Implemented an **Idempotent Transactional Inbox Pattern** in PostgreSQL (`inventory_db.processed_events`). The Inventory Service atomically checks and logs `(event_id, order_id)` before applying any stock reservation. Duplicate event replays are safely detected and discarded with zero duplicate reservations (verified: 10 replayed duplicates resulted in exactly 0 duplicate stock deductions).

### 4. Relational Write Contention vs Real-Time Search (CQRS)
* **The Failure Mode**: Running complex customer order searches, status filtering, and fuzzy SKU lookups directly against transactional relational tables creates shared row locks, table scans, and degrades write throughput under peak traffic.
* **What We Solved**: Built a dedicated **CQRS Read Projection** using **OpenSearch 2.13**. As orders are published to Kafka, an asynchronous search consumer transforms and indexes documents into OpenSearch (measured 133.98ms indexing lag). Complex inverted-index queries achieve **19ms P50 / 30ms P95 latency**, completely isolating search traffic from the transactional database.

### 5. Controllable Backpressure & Thread Contention in Asynchronous Processing
* **The Failure Mode**: Running CPU-intensive machine learning simulations or ETA routing computations directly inside Kafka consumer threads causes thread-lock contention and unmanaged backpressure. Conversely, spawning threads for sub-millisecond tasks wastes more time in queue coordination than execution.
* **What We Solved**: Engineered a **Bounded Thread Pool and Priority Work Queue** in the Prediction Service. Empirically demonstrated worker scaling: lightweight tasks stay pinned to 1 worker to eliminate queue synchronization overhead, while CPU-heavy routing simulations scale to 8 workers, increasing throughput to **425.4 evt/s** with a **50.5ms P95 latency** and bounded memory usage.

### 6. Real-Time Checkout vs Mathematical Wave Optimization (Google OR-Tools)
* **The Failure Mode**: Customer checkout requires sub-5ms routing responses, whereas multi-warehouse batch fulfillment (balancing inventory availability, shipping costs, and warehouse outbound capacity limits) is an NP-hard combinatorial problem.
* **What We Solved**: Built a **Dual-Mode Optimization Engine**:
  * **Greedy Nearest-Feasible Heuristic**: Delivers instant (<2ms) routing decisions at checkout based on inventory proximity.
  * **Google OR-Tools SCIP Mixed-Integer Linear Programming (MILP)**: Solves global wave fulfillment across multiple Fulfillment Centers (`FC-HYD-01`, `FC-BLR-01`, `FC-DEL-01`), achieving **3% to 10% lower shipping and handling costs** ($340–$770 saved per 50–100 order wave) in 26–332ms solve time.

### 7. End-to-End Cross-Datastore Consistency Verification (0 Drift Across 2,332 Records)
* **The Failure Mode**: Eventual consistency in distributed systems is notoriously difficult to guarantee. Naive table-row count checks (`COUNT(orders) == COUNT(inventory)`) are mathematically invalid because different services process different event subsets.
* **What We Solved**: Designed an automated **Correlation Invariant Audit Harness** using `order_id` and `event_id` keys. The audit rigorously verifies that for every persistent order:
  1. A published outbox record exists.
  2. A corresponding inventory inbox record exists.
  3. A prediction record exists in the prediction store.
  4. An OpenSearch index document exists with matching status.
  5. There are zero duplicate inbox entries and zero Kafka consumer lag.
  * **Verified Live**: Across **2,332 audited orders**, the invariant audit reports **0 orphan orders, 0 missing inventory, 0 missing predictions, and 0 consistency drift**.

### 8. Interactive 3D Control Plane & End-to-End User Verification
* **The Failure Mode**: Distributed backends are often treated as black boxes without visual observability into message flow, and UI layers lack rigorous automated verification.
* **What We Solved**:
  * Designed an elevated, luxury dark control plane featuring **interactive Three.js 3D WebGL topology**, **gyroscope-style 3D tilt physics (`Card3D.tsx`)**, and ambient glassmorphism.
  * Authored a 6/6 green **Playwright Chromium E2E test suite** validating real user journeys (order ingress, token-bucket burst shedding, OpenSearch inverted index queries, ETA predictions, and MILP optimization).

---

## Architecture Overview

```text
                                  CLIENT / CALLER
                                         │
                                         ▼
                            ┌─────────────────────────┐
                            │   Spring Cloud Gateway  │
                            │         (:8080)         │
                            │  [Token Bucket Limiter] │
                            └────────────┬────────────┘
                                         │
            ┌────────────────────────────┼────────────────────────────┐
            │ POST /api/v1/orders        │ GET /api/v1/search         │ GET/POST /api/predictions
            ▼                            ▼                            ▼
  ┌───────────────────┐        ┌───────────────────┐        ┌───────────────────┐
  │   Order Service   │        │   Search Service  │        │Prediction Service │
  │      (:8081)      │        │      (:8084)      │        │      (:8085)      │
  └─────────┬─────────┘        └─────────┬─────────┘        └─────────┬─────────┘
            │                            │                            │
   [ACID Transaction]                    │                            │
            ▼                            │                            │
  ┌───────────────────┐                  │                            │
  │ PostgreSQL (5433) │                  │                            │
  │   [order_db]      │                  │                            │
  │ ├── orders        │                  │                            │
  │ └── outbox_events │                  │                            │
  └─────────┬─────────┘                  │                            │
            │                            │                            │
     Outbox Publisher                    │                            │
     (Transactional)                     │                            │
            │                            │                            │
            └────────────────┐           │                            │
                             ▼           │                            │
                 ┌───────────────────────┴───┐                        │
                 │    Apache Kafka Cluster   │                        │
                 │          (:9092)          │                        │
                 │ Topic: order.events.created│                       │
                 └───────────┬───────────────┘                        │
                             │                                        │
           ┌─────────────────┼─────────────────────────┐              │
           │                 │                         │              │
           ▼                 ▼                         ▼              │
 ┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐    │
 │ Inventory Service │ │   Search Service  │ │Prediction Service │    │
 │      (:8082)      │ │  (Search Consumer)│ │(Prediction Group) │    │
 └─────────┬─────────┘ └─────────┬─────────┘ └─────────┬─────────┘    │
           │                     │                     │              │
    [Inbox Pattern]              │               Worker Pool          │
           │                     │             (1-8 Threads)          │
           ▼                     ▼                     │              ▼
 ┌───────────────────┐ ┌───────────────────┐           ▼     ┌───────────────────┐
 │ PostgreSQL (5433) │ │OpenSearch Cluster │   ┌───────────────┤ Prediction Store  │
 │  [inventory_db]   │ │      (:9200)      │   │ Delivery ETA  │ (In-Memory / TTL) │
 │ ├── inventory     │ │  [orders-index]   │   │  Calculators  └─────────┬─────────┘
 │ └── processed_evts│ └───────────────────┘   └───────┬───────┘         │
 └───────────────────┘                                 │                 │
                                                       ▼                 ▼
                                             ┌───────────────────────────────────┐
                                             │     Wave Optimization Engine      │
                                             │   Greedy (<2ms) vs MILP (SCIP)    │
                                             │       Google OR-Tools Batch       │
                                             └───────────────────────────────────┘

 ════════════════════════════════════════════════════════════════════════════════════════
                        OBSERVABILITY & MONITORING MESH
 ┌──────────────────────────────────────────────────────────────────────────────────────┐
 │ • Prometheus (:9090) — 15s Scrape Interval across all 5 Services + Kafka JMX        │
 │ • Grafana (:3000)    — Real-time Dashboards for Ingress, Consumer Lag & Error Rates  │
 │ • Micrometer Metrics — 9 Domain Metric Families (orders, lag, inventory, duration)   │
 └──────────────────────────────────────────────────────────────────────────────────────┘
```

> **Detailed Architecture & Transaction Boundaries:** See [docs/architecture.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/architecture.md)

---

## Key Engineering Competencies Demonstrated

| Capability | Engineering Implementation |
| :--- | :--- |
| **Distributed Systems** | Microservice topology, Kafka event streams, consumer groups, transactional outbox pattern |
| **Asynchronous Decoupling** | Transactional outbox table (`order_db.outbox_events`) polled and published to Kafka asynchronously |
| **Distributed Concurrency & Idempotency** | Inbox deduplication pattern (`inventory_db.processed_events`) preventing duplicate allocations on message re-delivery |
| **CQRS & Inverted Search** | OpenSearch 2.12 indexing for sub-30ms full-text and multi-faceted product & order queries |
| **Mathematical Optimization** | Greedy baseline (<2ms checkout) vs Mixed Integer Linear Programming (OR-Tools SCIP, 26–332ms wave planning) |
| **Distributed Predictions** | Horizontally-scaled multi-threaded worker pool with configurable intensity and stale TTL evaluation |
| **Fault Tolerance & Chaos Resilience** | Proven 100% order intake during full downstream outages (Inventory kill, OpenSearch stop, Kafka broker kill) |
| **Observability Mesh** | Prometheus metrics instrumentation across all services, Micrometer timers/counters, and Grafana dashboards |

## Project Structure

```text
scalefulfill/
├── docs/
│   ├── engineering-spec.md            # Master Engineering Specification
│   ├── architecture.md                # Master Architecture & Data Flow Reference
│   ├── amazon-sde-walkthrough.md      # Amazon SDE Technical Walkthrough & Presentation Story
│   ├── failure-modes.md               # Failure modes, mitigation strategies & recovery procedures
│   ├── adr/                           # Architecture Decision Records (ADRs 001–013)
│   └── benchmarks/                    # Phase 1 through Phase 7 Empirical Benchmark Reports
├── services/
│   ├── api-gateway/                   # Spring Cloud Gateway with Redis Token-Bucket Limiter (:8080)
│   ├── order-service/                 # Order Ingress & PostgreSQL Transactional Outbox (:8081)
│   ├── inventory-service/             # Multi-FC Inventory & Idempotent Inbox Consumer (:8082)
│   ├── search-service/                # OpenSearch CQRS Read Projection & Query Engine (:8084)
│   └── prediction-service/            # Distributed Worker Pool & OR-Tools MILP Solver (:8085)
├── frontend/                          # React 19 + TypeScript + Vite Presentation Control Plane (:5173)
├── e2e/                               # Playwright Chromium End-to-End Test Suite (6/6 passing)
├── infrastructure/
│   ├── docker-compose.yml             # PostgreSQL (5433), Redis (6379), Kafka (9092), OpenSearch (9200)
│   └── observability/                 # Prometheus (9090) & Grafana (3000) configs & dashboards
├── scripts/                           # Automated benchmarking, chaos testing & consistency audit harnesses
└── tests/                             # Integration, unit, and reliability suites (48/48 passing)
```

---

## Engineering Progression (Phases 1–7 Frozen Baseline)

The engineering progression of ScaleFulfill is **frozen at Phase 7 as a verified baseline**. Each phase resolved a concrete bottleneck identified through measurement:

- [x] **Phase 1: In-Process Monolithic Baseline** — Single PostgreSQL DB with optimistic locking; measured peak throughput of 512 req/s with hot-SKU row contention ([docs/benchmarks/phase1-baseline.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/benchmarks/phase1-baseline.md))
- [x] **Phase 2: Synchronous Microservice Decomposition** — Separated Order and Inventory databases with Spring Cloud Gateway; measured 86.5% throughput collapse (68.86 req/s) due to synchronous HTTP coupling and cascading thread exhaustion ([docs/benchmarks/phase2-decomposition.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/benchmarks/phase2-decomposition.md))
- [x] **Phase 3: Asynchronous Event Fabric & Transactional Outbox** — Replaced synchronous calls with Apache Kafka and the Transactional Outbox pattern; restored ingress latency to <15ms with 471.4 evt/s outbox burst ([docs/benchmarks/phase3-event-driven-kafka.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/benchmarks/phase3-event-driven-kafka.md))
- [x] **Phase 4: CQRS Read Path with OpenSearch** — Separated search projections from relational transaction tables; achieved 19ms P50 / 30ms P95 search latency with 133.98ms indexing lag ([docs/benchmarks/phase4-opensearch-search.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/benchmarks/phase4-opensearch-search.md))
- [x] **Phase 5: Full Observability Mesh** — Implemented Prometheus scraping across 5 scrape targets with 9 domain metric families; verified live detection of consumer lag and failure alerts ([docs/benchmarks/phase5-observability-verification.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/benchmarks/phase5-observability-verification.md))
- [x] **Phase 6: Distributed Predictions & Wave Optimization** — Parallel worker scaling (425.4 evt/s) and dual-mode optimization: Greedy (<2ms checkout) vs OR-Tools SCIP MILP (3–10% wave savings in 26–332ms) ([docs/benchmarks/phase6-prediction-optimization.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/benchmarks/phase6-prediction-optimization.md))
- [x] **Phase 7: End-to-End Reliability, Load & Failure Engineering** — Validated direct ingress (306.4 req/s peak), component outages (Inventory/OpenSearch/Kafka), duplicate delivery idempotency, and proved cross-datastore correlation invariant across 2,332 orders ([docs/benchmarks/phase7-reliability-engineering.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/benchmarks/phase7-reliability-engineering.md))

---

## Curated Interview-Grade Benchmark Table

The following 10 decisive empirical metrics demonstrate the architectural trade-offs across the progression:

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

## Testing Pyramid & Verification Architecture

ScaleFulfill employs a three-tiered testing pyramid that decouples domain correctness, distributed fault tolerance, and browser-facing user journeys:

```text
                  ▲
                 / \
                /   \
               /  ★  \       Playwright Browser E2E Journeys
              /───────\      Chromium end-to-end user flows (6/6 passing)
             /    ★    \
            /───────────\    Distributed Reliability & Chaos Test Harness
           /      ★      \   Phase 7 stress load, failover & correlation invariant audit
          /───────────────\
         /        ★        \ Service Unit & Integration Tests
        /───────────────────\ Spring Boot test slices & MockMvc test harness (48/48 passing)
```

| Layer | Technology | Scope & Invariants Verified | Status |
|---|---|---|:---:|
| **E2E Browser Journeys** | **Playwright (TypeScript / Chromium)** | Validates end-to-end user flows via React Presentation Control Plane: Order creation $\to$ outbox publishing $\to$ Kafka propagation $\to$ OpenSearch CQRS search $\to$ ETA prediction retrieval $\to$ Rate limit (HTTP 429) UI feedback $\to$ MILP wave optimization. | **6/6 Passing** |
| **Distributed Chaos & Reliability** | **PowerShell & REST / JQ Test Harness** | Validates sustained ingress load (306.4 req/s), token-bucket burst shedding, component kill/failover (PostgreSQL, Kafka, OpenSearch, Inventory), duplicate delivery idempotency, and proves cross-datastore correlation invariant across 2,332 persistent records. | **100% Verified** |
| **Domain & Integration Tests** | **JUnit 5 / AssertJ / Mockito / MockMvc** | Service isolation, entity state machines, database constraints, transactional outbox persistence, inbox deduplication logic, and OR-Tools SCIP solver constraints. | **48/48 Passing** |

---

## Interactive Presentation UI & Playwright E2E Setup

A modern, glassmorphic React/TypeScript control plane (`frontend/`) and Playwright test suite (`e2e/`) are provided as the **portfolio demonstration and verification layer** (Phases 1–7 remain frozen as the validated backend core).

### 1. Launch the Presentation Control Plane
```powershell
# Navigate to frontend and start Vite development server
cd frontend
npm install
npm run dev
# Running on http://localhost:5173 (proxies to Gateway :8080 and Prediction Service :8085)
```

### 2. Run Playwright End-to-End Tests
```powershell
# In a separate terminal, run the Playwright test suite
cd e2e
npm install
npx playwright test

# Or view interactive test runner UI
npx playwright test --ui
```

---

> **Interview Preparation & Technical Walkthrough:**
> - [Amazon SDE Interview Toolkit](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/amazon-interview-prep.md) — 2-minute elevator pitch, 5-minute deep-dive, 18 interviewer Q&As, 8 STAR behavioral stories.
> - [Technical Walkthrough & Architecture Reference](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/amazon-sde-walkthrough.md) — Detailed narrative breakdown of all 7 architectural phases.

