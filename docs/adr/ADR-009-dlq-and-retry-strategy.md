# ADR-009: Dead Letter Queue (DLQ) & Consumer Retry Strategy

## Status
Accepted

## Context
When processing asynchronous events in distributed consumer groups, consumer failures fall into two categories:
1. **Transient Failures:** Database connection drops, momentary lock contention, or temporary network timeouts. These can resolve with retries and exponential backoff.
2. **Poison Messages / Non-Transient Failures:** Corrupted payloads, unparseable JSON schemas, missing mandatory fields, or non-recoverable business rule violations. If the consumer retries these indefinitely, the partition head-of-line is blocked forever, stalling all subsequent valid messages.

## Decision
We implement a **multi-tiered Retry and Dead Letter Queue (DLQ)** strategy using Spring Kafka `DefaultErrorHandler` and `DeadLetterPublishingRecoverer`.

### Invariants & Rules:
1. **Bounded Local Retries:** Consumers attempt up to 3 execution retries with backoff (500 ms – 1000 ms) for transient failures before escalating.
2. **Deterministic DLQ Routing:** If all retries are exhausted, the unprocessable `ConsumerRecord` is automatically routed to a dedicated Dead Letter topic named `<original_topic>.DLQ` (e.g. `order.events.created.DLQ`).
3. **Offset Acknowledgment Post-DLQ:** Once the poison record is successfully persisted to the DLQ topic, the consumer commits the offset on the original topic, unblocking partition processing for subsequent events.
4. **Error Context Preservation:** The DLQ record includes error metadata in its Kafka headers:
   - `kafka_dlt-exception-message`
   - `kafka_dlt-exception-stacktrace`
   - `kafka_dlt-original-topic`, `kafka_dlt-original-partition`, `kafka_dlt-original-offset`
5. **Replay Mechanism:** Operators can inspect the DLQ topic, diagnose the underlying bug, deploy a schema/code fix, and replay messages from the DLQ back to the main topic.

## Consequences
### Positive
- **No Head-of-Line Blocking:** Poison messages cannot freeze consumer partitions or degrade system throughput.
- **Zero Data Loss:** Failed messages are captured with complete diagnostic metadata rather than silently dropped.
- **Auditable & Replayable:** Provides an explicit, observable quarantine for problematic events.

### Negative / Trade-offs
- **Out-of-Order Downstream Processing:** Moving a failed event to the DLQ allows subsequent events for other entities to proceed, but if an entity has multiple dependent events, ordering is disrupted for that specific entity until replayed.
- **Storage & Monitoring Overhead:** DLQ topics must be actively monitored with alerting on non-zero lag or high consumer rates.
