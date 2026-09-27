# ScaleFulfill — Distributed Order Fulfillment & Delivery Intelligence Platform

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.7-blue.svg)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7.2-red.svg)](https://redis.io/)
[![OpenSearch](https://img.shields.io/badge/OpenSearch-2.13-teal.svg)](https://opensearch.org/)
[![Docker](https://img.shields.io/badge/Docker-Enabled-blue.svg)](https://www.docker.com/)

A production-grade, event-driven distributed e-commerce fulfillment platform engineered to handle high-concurrency order ingestion, partitioned relational storage, dynamic multi-center inventory allocation, mathematical fulfillment optimization, distributed ETA predictions, and comprehensive fault tolerance.

---

## Architecture Overview

```text
                         ┌──────────────────────┐
                         │   React Dashboard    │
                         │ Customer / Operator  │
                         └──────────┬───────────┘
                                    │ REST / WebSocket
                         ┌──────────▼───────────┐
                         │      API Gateway     │
                         │ Token Bucket / Auth  │
                         └──────────┬───────────┘
                                    │
             ┌──────────────────────┼──────────────────────┐
             │                      │                      │
             ▼                      ▼                      ▼
      ┌─────────────┐       ┌─────────────┐       ┌──────────────┐
      │Order Service│       │Inventory Svc│       │Search Service│
      │ Java/Spring │       │ Java/Spring │       │ OpenSearch   │
      └──────┬──────┘       └──────┬──────┘       └──────────────┘
             │                     │
             └──────────┬──────────┘
                        ▼
                ┌───────────────┐
                │ Kafka Cluster │
                │ Event Stream  │
                └───────┬───────┘
                        │
          ┌─────────────┼─────────────────┐
          │             │                 │
          ▼             ▼                 ▼
   ┌────────────┐ ┌──────────────┐ ┌───────────────┐
   │ Fulfillment│ │ ETA Predictor│ │ Notification  │
   │ Optimizer  │ │ (FastAPI ML) │ │ Worker        │
   └────────────┘ └──────────────┘ └───────────────┘
```

---

## Key Engineering Competencies Demonstrated

| Capability | Engineering Implementation |
| :--- | :--- |
| **Distributed Systems** | Microservice topology, Kafka event streams, consumer groups, transactional outbox pattern |
| **Partitioned Storage** | 3-way consistent hash routing across PostgreSQL shards (`hash(customer_id) % 3`) |
| **Distributed Concurrency** | Redlock-style distributed locking on SKU reservation to prevent overselling |
| **Idempotency** | Atomic Redis verification with distributed locks and persistent DB unique constraints |
| **Mathematical Optimization** | Greedy baseline vs Mixed Integer Linear Programming (OR-Tools) for multi-FC allocation |
| **Distributed Predictions** | Horizontally-scaled Python prediction workers consuming partitioned Kafka event streams |
| **Search & Inverted Indexes** | OpenSearch indexing for sub-30ms full-text and multi-faceted product & order queries |
| **Fault Tolerance & Resilience**| Circuit breakers (Resilience4j), automatic retries with jitter, and Dead Letter Queue (DLQ) replay |
| **Observability** | Prometheus metrics instrumentation, OpenTelemetry distributed tracing, and Grafana dashboards |

---

## Project Structure

```text
scalefulfill/
├── docs/
│   ├── engineering-spec.md       # Master Engineering Specification
│   ├── architecture.md           # Deep dive architecture documentation
│   └── adr/                      # Architecture Decision Records (ADRs)
├── services/
│   ├── api-gateway/              # Spring Cloud Gateway / Reverse Proxy
│   ├── order-service/            # Order Management & Sharded Ingress
│   ├── inventory-service/        # Multi-FC Inventory & Distributed Locking
│   ├── search-service/           # OpenSearch Ingestion & Query Service
│   ├── fulfillment-optimizer/    # Multi-criteria FC allocation engine
│   └── prediction-service/       # Distributed ML ETA inference workers
├── infrastructure/
│   ├── docker/                   # Docker Compose manifests for Kafka, Redis, Postgres, OpenSearch
│   └── k8s/                      # Kubernetes deployment manifests
├── frontend/                     # React + TypeScript Operations & Customer UI
└── tests/                        # End-to-end integration and load testing suites
```

---

## Roadmap

- [x] **Step 1:** Master Engineering Specification & Design Freeze ([docs/engineering-spec.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/engineering-spec.md))
- [x] **Phase 1:** Monolith Order & Inventory Core with PostgreSQL & Concurrency Tests ([docs/benchmarks/phase1-baseline.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/benchmarks/phase1-baseline.md))
- [x] **Phase 2:** Service Decomposition, API Gateway & Synchronous Resilience ([docs/benchmarks/phase2-decomposition.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/benchmarks/phase2-decomposition.md))
- [x] **Phase 3:** Kafka Event Fabric, Transactional Outbox & Idempotent Consumers ([docs/benchmarks/phase3-event-driven-kafka.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/benchmarks/phase3-event-driven-kafka.md))
- [x] **Phase 4:** OpenSearch Distributed Indexing & Search ([docs/benchmarks/phase4-opensearch-search.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/benchmarks/phase4-opensearch-search.md))
- [x] **Phase 5:** Full Observability Stack (Prometheus + Grafana + Failure Telemetry) ([docs/benchmarks/phase5-observability-verification.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/benchmarks/phase5-observability-verification.md))
- [x] **Phase 6:** Distributed Prediction & Mathematical Optimization Engine (OR-Tools vs Greedy, Workload Scaling) ([docs/benchmarks/phase6-prediction-optimization.md](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/benchmarks/phase6-prediction-optimization.md))
- [ ] **Phase 7:** End-to-End Reliability, Load & Failure Engineering (Chaos Testing, Service Outages, Lag Drainage, DLQ Recovery, and Consistency Validation)
- [ ] **Phase 8:** Final Production Showcase & Amazon SDE Technical Walkthrough

---

## Empirical Benchmark Comparison Across All Architectural Phases

| Metric / Scenario | Phase 1 Monolith | Phase 2 Distributed Synchronous | Phase 3 Event-Driven (Kafka + Outbox) | Phase 4 CQRS Distributed Search (OpenSearch) | Phase 5 Full Observability Stack | Phase 6 Prediction & Mathematical Optimization |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Architecture Topology** | Single Process (In-Memory Calls) | Client -> API Gateway -> Order Svc -> Inventory Svc | Gateway -> Order Svc -> Outbox -> Kafka -> Idempotent Consumer | Gateway -> Search Svc -> OpenSearch 2.12 (asynchronously fed via Kafka) | 4 Microservices + Prometheus + Grafana | Dedicated `prediction-service` + OR-Tools SCIP MILP Solver Engine |
| **Throughput / Latency** | 512 req/s (P95: 18ms) | 68.86 req/s (P95: 75.3ms) | Ingress <15.0ms (471.4 evt/s outbox burst) | 19.0 ms (P50), 30.0 ms (P95) | 5/5 Targets UP, 9 Custom Metric Families | **Workload A (Lightweight):** 449.5 evt/s (1w) vs 314.4 evt/s (8w)<br>**Workload B (CPU-Heavy):** 344.0 evt/s (1w) → 425.4 evt/s (8w), P95: 68.3ms → 50.5ms |
| **Decision / Calculation Mode** | Static relational queries | Synchronous HTTP calls | Asynchronous transactional outbox | Inverted index search & aggregation | Full-system failure detection & metric scrape | **Greedy (<2ms):** Fast for real-time checkout<br>**MILP (26–332ms):** 3–10% cost savings ($340–$770/wave) for scheduled wave dispatch |
| **Resilience & Backpressure** | Database row lock wait | Circuit breaker & Redis rate limiting | Poison-pill quarantine in Kafka DLQ | Zero intake impact during OpenSearch outage | Automated detection of Kafka consumer lag & outage | Bounded worker thread pool (queue: 1,000) with TTL stale prediction filtering |
