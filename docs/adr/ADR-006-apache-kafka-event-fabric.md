# ADR-006: Apache Kafka as Distributed Asynchronous Event Fabric

## Status
Accepted

## Context
In Phase 2, decomposing the monolith into independent microservices with dedicated PostgreSQL databases exposed the limitations of synchronous REST/HTTP coupling:
1. **Latency Inflation:** Single-client latency increased from 32 ms to 57 ms, and 10-client P95 degraded to 406.89 ms due to synchronous network hops, connection pool contention, and serialization overhead.
2. **Throughput Degradation:** Peak throughput dropped from 512.6 req/s to 68.86 req/s because worker threads in `order-service` remained blocked while waiting for `inventory-service` HTTP responses.
3. **Temporal Coupling:** If downstream services (Inventory, ETA Prediction, Analytics) experience intermittent slowdowns or outages, order creation is stalled or rejected.

To decouple ingestion from processing and establish an event-driven backbone, we require an asynchronous, durable, partitioned distributed message fabric.

## Decision
We adopt **Apache Kafka** (KRaft mode) as the central event streaming fabric for ScaleFulfill.

### Invariants & Rules:
1. **Partitioning Key Strategy:** Events are partitioned by `aggregateId` (e.g., `orderId`). This guarantees total event ordering per entity while distributing load across parallel partitions.
2. **Consumer Group Isolation:** Downstream services consume via distinct consumer groups (e.g., `inventory-service-group`, `prediction-service-group`, `analytics-service-group`), allowing independent scaling and offset tracking.
3. **Producer Durability:** Producers use `acks=all` with at least 3 retries and idempotent producers enabled to prevent data loss or duplicate broker batches.
4. **Standard Envelope Contract:** All messages adhere to a typed `EventEnvelope<T>` containing `eventId`, `eventType`, `aggregateType`, `aggregateId`, `occurredAt`, `correlationId`, and `payload`.

## Consequences
### Positive
- **Temporal Decoupling:** Order ingestion completes as soon as local persistence succeeds; downstream consumers process at their own pace without blocking clients.
- **Horizontal Scalability:** Kafka topics partitioned across multiple consumers enable linear scale-out of worker processes.
- **Multi-Consumer Broadcast:** New services (such as ETA prediction or real-time analytics) can tap into order events without modifying `order-service`.

### Negative / Trade-offs
- **Eventual Consistency:** System transitions from immediate consistency to eventual consistency; inventory is reserved asynchronously.
- **Operational Complexity:** Requires managing Kafka clusters, partition balance, consumer group offsets, and lag monitoring.
