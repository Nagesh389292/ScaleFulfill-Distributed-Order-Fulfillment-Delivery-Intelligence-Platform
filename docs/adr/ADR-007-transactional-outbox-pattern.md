# ADR-007: Transactional Outbox Pattern for Dual-Write Consistency

## Status
Accepted

## Context
When emitting events to Apache Kafka upon entity state changes (e.g. `Order` created), a distributed dual-write problem arises if the application attempts to write to the database and publish to Kafka sequentially:
1. **DB Commit Succeeds, Kafka Publish Fails:** The order is stored in PostgreSQL, but Kafka never receives `OrderCreated`. Downstream consumers never process the order (phantom order in DB).
2. **Kafka Publish Succeeds, DB Commit Fails:** Consumers process the `OrderCreated` event, reserve inventory, or dispatch delivery, while the order record was rolled back in PostgreSQL (ghost processing).
3. **Two-Phase Commit (2PC / XA) is Infeasible:** XA transactions across heterogeneous relational databases and Kafka brokers introduce severe latency, coordinator bottlenecks, and availability fragility.

## Decision
We implement the **Transactional Outbox Pattern** within `order-service`.

### Invariants & Rules:
1. **Single ACID Transaction:** The business entity mutation (`INSERT INTO orders`) and the event record (`INSERT INTO outbox_events`) are executed within the **same local PostgreSQL transaction**.
2. **Schema Contract:** The `outbox_events` table contains:
   - `id` (UUID primary key)
   - `aggregate_type`, `aggregate_id`, `event_type`
   - `payload` (JSONB / text)
   - `status` (`PENDING`, `PUBLISHED`, `FAILED`)
   - `retry_count`, `created_at`, `published_at`, `error_message`
3. **Decoupled Publishing:** An asynchronous `OutboxPublisher` polls `PENDING` events (indexed on `(status, created_at)`), transmits them to Kafka partitioned by `aggregate_id`, and transitions their status to `PUBLISHED` upon broker acknowledgment.
4. **Resilient Retry & Backoff:** If Kafka publishing fails, the outbox record increments `retry_count` and updates `error_message` for safe retry without rolling back the completed customer order.

## Consequences
### Positive
- **Guaranteed At-Least-Once Publishing:** No event is lost if the broker is temporarily unreachable or the application process restarts mid-flight.
- **Strict Atomicity:** Orders cannot exist without corresponding outbox events, and events cannot exist without committed orders.
- **Zero Distributed Locks / 2PC:** Relies exclusively on standard, battle-tested PostgreSQL local ACID properties.

### Negative / Trade-offs
- **Publishing Lag:** Events are published with a sub-second delay depending on the publisher polling interval.
- **Outbox Table Bloat:** Requires periodic housekeeping/archiving of `PUBLISHED` events to prevent unbounded table growth.
- **Potential Duplicate Events:** Network partitions between publisher and Kafka can result in re-publishing, mandating idempotent consumers downstream.
