# Phase 3 Event-Driven Architecture, Transactional Outbox & Kafka Benchmark Report

**Architecture:** Client -> API Gateway (8080) -> Order Service (8081) -> PostgreSQL Outbox (`order_db`) -> Kafka Publisher -> Apache Kafka 3.7 (`order.events.created`, 4 Partitions) -> Inventory Consumer (`inventory-service`, group: `inventory-service-group`) -> Inbox Deduplication (`inventory_db.processed_events`)
**Date:** 2026-09-27 20:34:00
**Infrastructure:** Apache Kafka 3.7 (KRaft, 4 Partitions), PostgreSQL 16 (isolated `order_db` and `inventory_db`), Redis 7.2

---

## 1. Empirical Verification Test Results

| Test # | Test Scenario | Expected Behavior | Observed Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Test 1** | **End-to-End Live Order Flow** | `POST /api/v1/orders` -> Order saved & outbox `PENDING` -> Publisher sends to Kafka -> Outbox `PUBLISHED` -> Inventory reserved -> Inbox entry created | Order `ORD-9D56236D` created in `order_db`; Outbox status transitioned to `PUBLISHED` in 435ms; 10 units reserved in `inventory_db`; Inbox record inserted in `processed_events`. | **PASSED** |
| **Test 2** | **Dual-Write Outbox Protection during Kafka Outage** | Kafka broker paused -> Order transaction commits locally with outbox `PENDING` -> Broker restored -> Publisher retries & succeeds -> Outbox `PUBLISHED` -> Inventory reserved | Kafka container paused; Order `ORD-5406C1FB` saved with outbox `PENDING\|0`; Kafka unpaused; Publisher cleanly recovered, status updated to `PUBLISHED`, and stock reserved downstream. | **PASSED** |
| **Test 3** | **Duplicate Event Deduplication (Inbox Pattern)** | Re-publishing identical `eventId` to Kafka is detected by consumer; business side-effects occur strictly once | `PROD-103` initial stock: 1000. 1st delivery: stock dropped to 990 (10 reserved), inbox count = 1. 2nd delivery of duplicate: duplicate detected, reservation skipped, stock remained 990 (0 delta), inbox count = 1. | **PASSED** |
| **Test 4** | **Consumer Offset Recovery** | Consumer resumes from uncommitted partition offsets after interruption without message loss | Injected 5 events; consumer resumed from committed offset; all 5 records ingested and acknowledged with zero message loss. | **PASSED** |
| **Test 5** | **Poison Message & Dead Letter Queue (DLQ)** | Malformed event retried 3 times with backoff, routed to `order.events.created.DLQ`, unblocking main topic | Injected malformed JSON `BAD-POISON-1790521293`; ErrorHandler retried 3 times (500ms backoff); Record quarantined in `order.events.created.DLQ`; Verified message in DLQ topic. | **PASSED** |
| **Test 6** | **Partition Ordering via AggregateId** | Partition key = `aggregateId` guarantees all events for an order map to the same partition in FIFO order | 3 chronological events (`OrderCreated`, `InventoryReserved`, `FulfillmentAssigned`) for `ORD-ORDERING-1790521304` routed to the exact same Kafka partition in strict sequence. | **PASSED** |
| **Test 7** | **Consumer Group Scaling & Partition Rebalancing** | Scaling from 1 to 2 consumers rebalances topic partitions across active workers | 1 Consumer: owned 4/4 partitions `[0, 1, 2, 3]`. 2 Consumers: partition rebalance assigned `[2, 3]` to Consumer 1 and `[0, 1]` to Consumer 2. | **PASSED** |
| **Test 8** | **Throughput & Consumer Lag Benchmark** | Bulk order injection processed asynchronously with zero message loss and lag draining to 0 | 1,000 order events published in 2.12s (471.4 events/sec); Lag drained to 0 across both 1-consumer and 2-consumer configurations. | **PASSED** |

---

## 2. Consumer Throughput & Scalability Benchmark

Measurements taken by publishing batches of 1,000 fully formed order events with individual database transactions and inbox insertions per message:

| Metric | Phase 2 Synchronous Decomposition | Phase 3 Kafka (1 Consumer Instance) | Phase 3 Kafka (2 Consumer Instances) | Architectural Impact |
| :--- | :--- | :--- | :--- | :--- |
| **Client Ingestion Latency** | 57.09 ms (P50) / 406.89 ms (P95) | **< 15.0 ms** (Local DB Commit) | **< 15.0 ms** (Local DB Commit) | **~73% reduction in client perceived latency**; client unblocked immediately upon Outbox insert |
| **Ingestion Coupling** | Tight (Order thread blocks on Inventory HTTP) | Completely Decoupled (Asynchronous Kafka Event Fabric) | Completely Decoupled (Asynchronous Kafka Event Fabric) | Downstream latency or temporary inventory unavailability does not block order ingestion |
| **Consumer Drain Time (1,000 events)** | N/A (synchronous) | 32.81 s | **28.55 s** | Horizontal scaling across Kafka partitions reduces drain duration |
| **Consumer Processing Throughput** | 68.86 req/s (Peak blocking) | 30.48 events/s | **35.02 events/s** | Parallel consumption across distributed partition owners |
| **Average Consumer Processing Latency** | N/A | 32.81 ms/event | **28.55 ms/event** | Includes deserialization, inbox query, inventory update, and inbox insert |
| **Max Consumer Lag** | N/A | 1,000 -> 0 | 1,000 -> 0 | Zero message loss; all events safely processed and committed |

---

## 3. Distributed Architecture Comparison Across Phases

```text
========================================================================================
Phase 1 (Monolith):
  Client -> Monolith -> PostgreSQL (Single DB)
  Bottleneck: Optimistic-lock contention on hot inventory rows (87.9% conflict rate at C=25).

Phase 2 (Microservices - Synchronous HTTP):
  Client -> API Gateway -> Order Service -> HTTP -> Inventory Service -> Separate DBs
  Overhead: Distributed latency increase (P50: 32ms -> 57ms, P95: 46ms -> 406ms).
  Ceiling: Synchronous thread blocking across tiers (Throughput: 512 req/s -> 68.8 req/s).

Phase 3 (Event-Driven Architecture - Kafka + Outbox + Inbox):
  Client -> API Gateway -> Order Service -> Local DB (orders + outbox) -> 201 Created (<15ms)
                                                │
                                         Outbox Publisher
                                                ▼
                                        Apache Kafka (4 Partitions)
                                                │
                                    ┌───────────┴───────────┐
                                    ▼                       ▼
                           Consumer Instance 1     Consumer Instance 2
                            (Partitions 2, 3)       (Partitions 0, 1)
                                    │                       │
                                    ▼                       ▼
                             Inbox Check (processed_events) -> Inventory DB
  Advantages:
  1. Client latency completely decoupled from downstream processing.
  2. Transactional Outbox guarantees zero lost events during broker outages.
  3. Relational Inbox guarantees exactly-once business side effects under duplicate delivery.
  4. Dead Letter Queue isolates poison pills without stalling partition processing.
  5. Horizontal scalability enabled via Kafka partition rebalancing.
========================================================================================
```

---

## 4. Phase 3 Definition of Done Checklist

* [x] **Kafka Infrastructure:** Apache Kafka 3.7 (KRaft mode) running on port 9092 with 4 partitions.
* [x] **Topic Creation:** `order.events.created` and `order.events.created.DLQ` active.
* [x] **Consumer Groups:** `inventory-service-group` running with dynamic partition balancing.
* [x] **Transactional Consistency:**
  - `outbox_events` table in `order_db` with index on `(status, created_at)`.
  - Atomic local transaction committing order and outbox event simultaneously.
  - Outbox publisher polling, transmitting to Kafka, and updating status to `PUBLISHED`.
  - Dual-write resilience verified under broker failure.
* [x] **Idempotency & Deduplication:**
  - `processed_events` table in `inventory_db` with composite primary key `(event_id, consumer_group)`.
  - Duplicate delivery verified: inventory reserved once, duplicate delivery safely ignored.
* [x] **Failure Handling & DLQ:**
  - Poison message retried 3 times with backoff.
  - Automatic escalation and routing to `order.events.created.DLQ`.
* [x] **Partition Ordering:** Partition key `aggregateId` verifies sequential processing per order.
* [x] **Documentation:**
  - [ADR-006: Apache Kafka Event Fabric](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/adr/ADR-006-apache-kafka-event-fabric.md)
  - [ADR-007: Transactional Outbox Pattern](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/adr/ADR-007-transactional-outbox-pattern.md)
  - [ADR-008: Idempotent Consumer Pattern](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/adr/ADR-008-idempotent-consumer-pattern.md)
  - [ADR-009: DLQ & Retry Strategy](file:///c:/Users/NAGESH%20REDDY/Desktop/New%20folder/docs/adr/ADR-009-dlq-and-retry-strategy.md)
  - Live benchmark report (`docs/benchmarks/phase3-event-driven-kafka.md`).
