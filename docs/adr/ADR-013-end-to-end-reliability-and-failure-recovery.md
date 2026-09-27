# ADR-013: End-to-End Reliability, Fault Injection, and Distributed Consistency Invariant

## Status
Accepted

## Context
ScaleFulfill has evolved through six distinct architectural phases:
1. **Phase 1:** In-process transactional correctness with PostgreSQL optimistic locking.
2. **Phase 2:** Microservice decomposition with independent databases, API Gateway routing, and Resilience4j circuit breakers.
3. **Phase 3:** Asynchronous event fabric using Apache Kafka, the Transactional Outbox pattern, and idempotent inbox consumers.
4. **Phase 4:** CQRS read projection via OpenSearch distributed indexing.
5. **Phase 5:** Full observability with Prometheus scraping and custom failure telemetry.
6. **Phase 6:** Asynchronous delivery ETA prediction workloads and mathematical wave fulfillment optimization (Google OR-Tools SCIP MILP).

While each individual component has unit, integration, and isolated benchmark coverage, distributed systems fail primarily at interaction boundaries under concurrent load and partial component failure. Rather than accumulating peripheral features, the system requires definitive **End-to-End Reliability and Scale Engineering** validation to verify that:
1. Ingress decoupling protects order intake when downstream consumers (Inventory, Search, Prediction) fail.
2. Transactional outbox durability protects business events when the message broker (Kafka) fails.
3. At-least-once delivery guarantees do not produce duplicate reservations or inconsistent states when consumers crash.
4. Distributed state remains mathematically consistent across all storage engines once failures recover.

## Decision

We establish an automated **End-to-End Reliability Test Harness** executing five progressive failure and recovery experiments against the live multi-service cluster:

### 1. Sustained Load Ramp (50 → 100 → 200 → 300 req/s)
- Dispatches concurrent customer orders through API Gateway (:8080) while simultaneously generating read queries against Search Service (:8084).
- Measures throughput, latency percentiles (P50, P95, P99), gateway rate-limiting enforcement, and Kafka consumer lag under sustained pressure.

### 2. Downstream Service Outage (Inventory Service Kill & Recovery)
- Injects a complete failure of `inventory-service` while order ingress continues.
- **Architectural Expectation:** Ingress does NOT block synchronously (unlike Phase 2). Orders commit to `order_db` and append to `outbox_events`.
- Upon `inventory-service` restart, the Kafka consumer group reconnects, drains accumulated lag, and executes pending SKU reservations without event loss.

### 3. Search Engine Outage (OpenSearch Container Outage & Drainage)
- Injects an ungraceful shutdown of the OpenSearch cluster (`docker stop scalefulfill-opensearch`).
- Order placement and transactional persistence continue unabated.
- Measures Kafka consumer lag accumulation on `search-service-group`.
- Upon container restart, measures **lag-drain velocity (events/sec)** and verifies search index synchronization.

### 4. Broker Infrastructure Failure (Kafka Broker Outage & Outbox Backpressure)
- Stops the Kafka message broker (`docker stop scalefulfill-kafka`) during active order intake.
- **Architectural Expectation:** Orders continue committing to PostgreSQL because the Transactional Outbox pattern decouples the database transaction from network broker availability.
- Outbox publisher encounters broker connection timeouts and backs off.
- Upon Kafka restart, the publisher resumes polling `outbox_events WHERE status = 'PENDING'` and publishes all buffered events in sequence.

### 5. Consumer Crash & Idempotency / Duplicate Redelivery
- Simulates network duplicates and consumer restart re-deliveries by replaying previously committed Kafka order events with identical `eventId`s.
- **Architectural Expectation:** Downstream consumer inbox tables (`processed_events`) detect duplicate signatures and deduplicate without double-deducting inventory or duplicating predictions.

### 6. The End-to-End Consistency Invariant
Following the resolution of all failure injection scenarios and complete Kafka lag drainage to zero, the harness executes an automated cross-database audit verifying the master distributed invariant:

$$\text{Count}(\text{Orders}_{\text{Postgres}}) = \text{Count}(\text{OutboxPublished}_{\text{Postgres}}) = \text{Count}(\text{Reservations}_{\text{Inventory}}) = \text{Count}(\text{Predictions}_{\text{Store}}) = \text{Count}(\text{Docs}_{\text{OpenSearch}})$$

## Consequences

### Positive
- Validates the architectural transition from Phase 2 (synchronous failure cascade) to Phase 3–6 (resilient, asynchronous decoupling).
- Empirically proves the correctness of the Transactional Outbox pattern during live broker outages.
- Demonstrates zero data loss ($0\text{ dropped orders}$) across all component outage scenarios.
- Provides quantitative recovery metrics: Recovery Time Objective (RTO), lag accumulation rate, and consumer drain velocity.

### Negative / Trade-offs
- Chaos tests temporarily disrupt background service processes and require automated health verification and orchestrator coordination.
- Full consistency verification requires cross-datastore querying across PostgreSQL, OpenSearch, and in-memory prediction registries.
