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
- [ ] **Phase 4:** OpenSearch Distributed Indexing & Search
- [ ] **Phase 5:** Distributed ETA Prediction Engine
- [ ] **Phase 6:** Mathematical Fulfillment Optimization (Greedy vs LP)
- [ ] **Phase 7:** Distributed Relational Sharding & Redis Concurrency
- [ ] **Phase 8:** Fault Tolerance, Circuit Breakers & DLQ Replay
- [ ] **Phase 9:** Observability & Distributed Tracing (Prometheus/Grafana)
- [ ] **Phase 10:** Operations Copilot & Operations Dashboard

---

## Phase 1 vs Phase 2 vs Phase 3 Empirical Benchmark Comparison

| Metric / Scenario | Phase 1 Monolith | Phase 2 Distributed Synchronous | Phase 3 Event-Driven (Kafka + Outbox) | Architectural Explanation |
| :--- | :--- | :--- | :--- | :--- |
| **Architecture Topology** | Single Process (In-Memory Calls) | Client → API Gateway → Order Svc → Inventory Svc | Gateway → Order Svc → Outbox → Kafka → Idempotent Consumer | Asynchronous choreography with durable partitioned event log |
| **Database Ownership** | Shared PostgreSQL instance | Isolated `order_db` & `inventory_db` | Isolated `order_db` & `inventory_db` with Outbox & Inbox tables | Full database isolation with zero distributed dual-write inconsistencies |
| **Client Ingestion Latency** | 32.0 ms (P50) | 57.09 ms (P50) / 406.89 ms (P95) | **< 15.0 ms (Local DB Commit)** | **~73% drop in client latency**; client unblocked upon local Outbox insert |
| **Peak Throughput / Rate** | 512.6 req/s | 68.86 req/s (Synchronous blocking) | **471.4 events/sec ingestion** (Async Kafka) | Removes thread pool blocking across inter-service network boundaries |
| **Dual-Write Resilience** | N/A (single ACID DB) | Unprotected (partial failures risk orphaned state) | **Guaranteed via Transactional Outbox** | Order and Outbox event commit atomically; broker outages never lose data |
| **Consumer Deduplication** | N/A | None | **Relational Inbox Table (`processed_events`)** | Transforms transport at-least-once delivery into exactly-once business side effects |
| **Edge Protection** | None | Token Bucket Rate Limiting (HTTP 429) | Token Bucket Rate Limiting (HTTP 429) | Limits excessive request bursts before they reach backend services |
| **Poison Pill Handling** | Process crash | Downstream rejection / 503 fallback | **Dead Letter Queue (DLQ) after 3 retries** | Quarantines corrupted messages into `order.events.created.DLQ` without blocking topic head |
