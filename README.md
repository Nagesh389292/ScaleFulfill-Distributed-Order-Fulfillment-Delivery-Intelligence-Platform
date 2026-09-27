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
- [ ] **Phase 5:** Distributed ETA Prediction Engine
- [ ] **Phase 6:** Mathematical Fulfillment Optimization (Greedy vs LP)
- [ ] **Phase 7:** Distributed Relational Sharding & Redis Concurrency
- [ ] **Phase 8:** Fault Tolerance, Circuit Breakers & DLQ Replay
- [ ] **Phase 9:** Observability & Distributed Tracing (Prometheus/Grafana)
- [ ] **Phase 10:** Operations Copilot & Operations Dashboard

---

## Phase 1 vs Phase 2 vs Phase 3 vs Phase 4 Empirical Benchmark Comparison

| Metric / Scenario | Phase 1 Monolith | Phase 2 Distributed Synchronous | Phase 3 Event-Driven (Kafka + Outbox) | Phase 4 CQRS Distributed Search (OpenSearch) | Architectural Explanation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Architecture Topology** | Single Process (In-Memory Calls) | Client -> API Gateway -> Order Svc -> Inventory Svc | Gateway -> Order Svc -> Outbox -> Kafka -> Idempotent Consumer | Gateway -> Search Svc -> OpenSearch 2.12 (asynchronously fed via Kafka) | Complete CQRS read/write segregation; zero read queries touch transactional OLTP databases |
| **Database Ownership** | Shared PostgreSQL instance | Isolated order_db & inventory_db | Isolated order_db & inventory_db with Outbox & Inbox tables | Isolated OLTP databases + Inverted Search Indices (orders-index, products-index) | High-cardinality indexing and full-text searches offloaded from relational storage |
| **Order Acceptance Boundary** | Synchronous Stock Reservation | Synchronous Distributed HTTP | Asynchronous local outbox commit (<15 ms) | Asynchronous local outbox commit (<15 ms) | Phase 3 removed synchronous downstream dependency from client path; local outbox commit completes without waiting for indexing |
| **Search / Query Latency** | Direct SQL query (locks / table scans) | Direct SQL query on Order DB | Direct SQL query on Order DB | **19.0 ms (P50), 30.0 ms (P95) in OpenSearch** | BM25 scoring and term dictionaries provide sub-30ms multi-attribute filtering |
| **Indexing Lag (DB Commit -> Searchable)** | 0 ms (immediate read-after-write) | 0 ms (immediate read-after-write) | N/A (no dedicated search index) | **133.98 ms (Empirical End-to-End Lag)** | Eventual consistency window for asynchronous Kafka event propagation |
| **Search Outage Resilience** | N/A | N/A | N/A | **Zero Data Loss (Kafka Buffered)** | Search outage produces 0% impact on order intake; consumer catches up upon OpenSearch recovery |
| **Edge Protection** | None | Token Bucket Rate Limiting (HTTP 429) | Token Bucket Rate Limiting (HTTP 429) | Token Bucket Rate Limiting (HTTP 429) | Protects search endpoints against scraper abuse and high-frequency bursts |
| **Dual-Write Resilience** | N/A (single ACID DB) | Unprotected (partial failures risk orphaned state) | Guaranteed via Transactional Outbox | Guaranteed via Outbox + Idempotent Kafka Consumer | Orders and outbox records commit atomically; Kafka delivers events durably to search projection |
