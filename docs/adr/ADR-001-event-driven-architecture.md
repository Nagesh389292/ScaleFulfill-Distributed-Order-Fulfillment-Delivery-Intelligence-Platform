# ADR-001: Adoption of Asynchronous Event-Driven Architecture via Apache Kafka

## Status
Accepted

## Context
In our e-commerce fulfillment domain, an order lifecycle involves multiple downstream operations:
1. Validating and locking inventory across distributed warehouses.
2. Running mathematical optimization to select the most cost-effective fulfillment centers.
3. Invoking ML prediction models to calculate delivery ETAs.
4. Indexing order and shipment details into OpenSearch for real-time customer and operational visibility.
5. Emitting customer notifications (SMS/Email/Push).

If implemented synchronously over REST:
- **Cascading Latencies:** The client-facing order placement endpoint experiences the aggregate latency of all downstream dependencies ($P_{99} > 3\text{s}$).
- **Tight Coupling & Availability Degradation:** If the notification or prediction service undergoes a downtime or transient network degradation, the order checkout path fails or hangs.
- **Backpressure & Spike Vulnerability:** Traffic spikes (e.g., flash sales) overwhelm downstream prediction models and search indexing clusters without a buffering layer.

## Decision
We adopt an **Asynchronous Event-Driven Architecture** powered by **Apache Kafka** paired with the **Transactional Outbox Pattern** in the relational database layer:
1. The **Order Service** accepts an order, validates basic schema and idempotency, persists the order as `PENDING` into its local PostgreSQL shard, and writes an `OrderCreatedEvent` into the `transactional_outbox` table within the same ACID transaction.
2. A lightweight outbox publisher polls/streams events to the Kafka topic `order.events.created`.
3. Downstream services (`inventory-service`, `fulfillment-optimizer`, `prediction-service`, `search-service`) consume from this topic via independent consumer groups.
4. Failures in downstream services do not impede the customer's checkout path; events are retried with exponential backoff and ultimately routed to a Dead Letter Queue (`order.events.dlq`) upon persistent failures.

## Consequences
### Positive
- Client-facing order acceptance latency reduced to sub-80ms ($P_{95}$).
- Complete decoupling of core transactional ingress from analytical and prediction workloads.
- Guaranteed at-least-once delivery with partition-level ordering preserved per `order_id`.
- Natural buffering and elasticity during traffic spikes.

### Negative / Trade-offs
- Introduces eventual consistency for downstream views (search index, ETA availability).
- Requires robust idempotency and deduplication mechanisms at each consumer boundary.
- Increased operational complexity managing Kafka clusters, consumer offsets, and schema evolution.
