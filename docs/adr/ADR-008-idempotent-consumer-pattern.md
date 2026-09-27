# ADR-008: Idempotent Consumers with Relational Inbox Pattern

## Status
Accepted

## Context
In distributed event streaming architectures, network retries, broker rebalances, or consumer crash-restarts inevitably produce **at-least-once message delivery**:
1. A consumer processes an `OrderCreated` event and reserves 5 units of inventory in its database.
2. Before the consumer commits its Kafka offset, the worker process crashes or network latency causes a consumer group rebalance.
3. The new partition owner re-reads the unacknowledged `OrderCreated` record and re-executes the reservation.
4. Without idempotency, 10 units are reserved for a 5-unit order, corrupting inventory state and leading to artificial stock-outs.

## Decision
We implement the **Idempotent Consumer (Inbox) Pattern** across downstream consumer services (starting with `inventory-service`).

### Invariants & Rules:
1. **Inbox Deduplication Table:** Downstream databases maintain a dedicated `processed_events` table:
   - `event_id` (VARCHAR primary key)
   - `consumer_group` (VARCHAR composite primary key component)
   - `event_type` (VARCHAR)
   - `processed_at` (TIMESTAMP)
2. **Atomic Execution Boundary:** The business state transition (e.g. inventory reservation) and the insertion into `processed_events` must execute inside the **same local database transaction**.
3. **Pre-Execution Check:** Before executing any side-effects, the consumer queries `processedEventRepository.existsByEventIdAndConsumerGroup(eventId, consumerGroup)`:
   - If true: Log duplicate warning, immediately acknowledge the Kafka offset, and skip business logic.
   - If false: Execute business logic, insert inbox record, commit transaction, and acknowledge offset.
4. **Natural vs Artificial Idempotency:** Wherever possible, business keys (e.g. `order_id` in reservation records) are also bounded by unique constraints to provide defense-in-depth against duplicate processing.

## Consequences
### Positive
- **Exactly-Once Business Semantics:** Transforms at-least-once transport delivery into exactly-once business side effects.
- **Fail-Safe Recovery:** Consumers can safely re-read partitions from earlier offsets during incident recovery without corrupting domain state.
- **Auditability:** The `processed_events` table provides a definitive local audit log of all events processed by each consumer group.

### Negative / Trade-offs
- **Database Overhead:** Adds one read lookup and one write insert per consumed message to the consumer's local database.
- **Table Housekeeping:** Requires periodic TTL-based cleanup of old processed event IDs (e.g., retaining records for 14-30 days).
