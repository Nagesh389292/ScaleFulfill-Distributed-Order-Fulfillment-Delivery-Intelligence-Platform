# ScaleFulfill — Master Engineering Specification
**System:** Distributed Order Fulfillment & Delivery Intelligence Platform  
**Target Domain:** Distributed E-Commerce Logistics & Fulfillment Architecture  
**Author:** SDE Engineering Team  
**Status:** DRAFT (Under Review)  
**Version:** 1.0.0  

---

## 1. Executive Summary & Problem Formulation

### 1.1 Business Context
ScaleFulfill is a high-throughput, low-latency distributed order fulfillment platform engineered to handle dynamic multi-regional e-commerce operations. Modern fulfillment networks manage tens of millions of SKUs, thousands of fulfillment centers (FCs), and volatile supply-demand imbalances across geographic clusters. 

When an order arrives, naive monolithic systems face critical bottlenecks:
- **Cascading Failures:** Synchronous coupling between order placement, inventory verification, carrier routing, and payment leads to high tail latencies ($P_{99} > 3.5\text{s}$) and catastrophic outages when single downstream dependencies fail.
- **Race Conditions & Overselling:** High-concurrency flash sales induce stock overselling or phantom stockouts unless strict distributed concurrency control is enforced.
- **Suboptimal Fulfillment:** Selecting warehouses solely based on geographic proximity ignores inventory holding costs, carrier contract SLAs, and node utilization, resulting in bloated operational expenditures.
- **Unpredictable Deliveries:** Static ETAs fail to account for weather, warehouse dispatch backlogs, and real-time carrier congestion.

### 1.2 Core Platform Objective
ScaleFulfill decouples critical-path transactional ingress from asynchronous fulfillment orchestration, combining:
1. **Partitioned & Idempotent Ingress:** Sub-100ms order acceptance with guaranteed idempotency and horizontal database sharding.
2. **Event-Driven Fulfillment Core:** Apache Kafka event fabric coordinating inventory reservation, routing, and notifications.
3. **Multi-Objective Optimization Engine:** Algorithmic fulfillment center selection balancing shipping cost, transit duration, and FC inventory pressure.
4. **Distributed ML Prediction Pipeline:** Scalable inference workers running batch/stream ETA predictions over partitioned Kafka streams.
5. **Operational Elasticity & Resilience:** Distributed locking, Redis token-bucket rate limiting, circuit breaking, and dead-letter queue (DLQ) automated replay.

---

## 2. Requirements Specification

### 2.1 Functional Requirements (FR)
- **FR-01 (Order Ingestion):** Ingest multi-item customer orders via authenticated REST endpoints with strict client-provided `Idempotency-Key` headers.
- **FR-02 (Catalog & Inventory Management):** Maintain real-time available-to-promise (ATP) inventory levels across multiple fulfillment centers with atomic decrements and reservation holds.
- **FR-03 (Fulfillment Routing & Allocation):** Given an order and customer destination, evaluate candidate fulfillment centers and allocate item quantities to minimize shipping expense and transit time without breaking stock invariants.
- **FR-04 (Delivery ETA Estimation):** Dynamically compute delivery completion estimates based on warehouse processing backlogs, origin-destination transit metrics, and environmental parameters.
- **FR-05 (Distributed Search & Filtering):** Provide sub-50ms full-text and facet search over products, customer orders, and dispatch manifests.
- **FR-06 (Asynchronous Lifecycle Tracking):** Progress orders through deterministic states (`PENDING`, `INVENTORY_RESERVED`, `ALLOCATED`, `DISPATCHED`, `DELIVERED`, `CANCELLED`, `FAILED`) via event-driven pub/sub.
- **FR-07 (Operational Copilot & Telemetry):** Expose Prometheus metrics, structured OpenTelemetry traces, and an automated diagnostics interface for operations engineers to isolate system bottlenecks.

### 2.2 Non-Functional Requirements (NFR)

| Metric | Target Specification | Enforcement Mechanism |
| :--- | :--- | :--- |
| **Ingress Latency** | $P_{95} \le 80\text{ ms}$, $P_{99} \le 180\text{ ms}$ | Redis caching, async dispatch, connection pooling |
| **Throughput** | 5,000 orders/sec steady-state, 15,000 peak | Sharded ingress, Kafka 16-partition topics, reactive consumers |
| **High Availability** | 99.95% uptime for order placement | Decoupled circuit breakers (Resilience4j), fallback queues |
| **Data Consistency** | Strong consistency for inventory; Eventual for Search/ETAs | Row-level DB locks / Redis distributed locks; Outbox pattern |
| **Idempotency** | Exactly-once semantics at application boundary | Redis atomic `SETNX` with TTL + Postgres unique constraint |
| **Fault Tolerance** | Zero message loss upon node crash | Kafka `acks=all`, consumer manual commits, DLQ with backoff |

---

## 3. System Architecture & Component Topology

```text
                           ┌────────────────────────────────────────┐
                           │          React Operations &            │
                           │          Customer Storefront           │
                           └───────────────────┬────────────────────┘
                                               │
                                       HTTPS / REST / WSS
                                               │
                                               ▼
                           ┌────────────────────────────────────────┐
                           │           API Gateway Service          │
                           │  - JWT Authentication                  │
                           │  - Redis Token-Bucket Rate Limiter     │
                           │  - Circuit Breakers & Reverse Proxy    │
                           └───────┬────────────────────────┬───────┘
                                   │                        │
               ┌───────────────────┴──────────┐             └───────────────┐
               ▼                              ▼                             ▼
    ┌────────────────────┐         ┌────────────────────┐        ┌────────────────────┐
    │   Order Service    │         │ Inventory Service  │        │   Search Service   │
    │  (Java 21 / Spring)│         │ (Java 21 / Spring) │        │ (Spring Boot / OS) │
    └─────────┬──────────┘         └──────────┬─────────┘        └──────────┬─────────┘
              │                               │                             │
              ▼                               ▼                             ▼
    ┌────────────────────┐         ┌────────────────────┐        ┌────────────────────┐
    │  PostgreSQL Shards │         │  PostgreSQL DB     │        │ OpenSearch Cluster │
    │  (Customer Hashed) │         │  (ATP Inventory)   │        │ (Products & Orders)│
    └─────────┬──────────┘         └──────────┬─────────┘        └────────────────────┘
              │                               │
              └───────────────┬───────────────┘
                              │ Transactional Outbox / CDC
                              ▼
                   ┌────────────────────────────────────────┐
                   │             Apache Kafka               │
                   │  Topics:                               │
                   │   - orders.created (Partitions: 8)     │
                   │   - inventory.allocated (Partitions: 8)│
                   │   - eta.predicted (Partitions: 8)      │
                   │   - orders.dlq (Partitions: 4)         │
                   └───────┬─────────────┬───────────┬──────┘
                           │             │           │
            ┌──────────────┘             │           └──────────────┐
            ▼                            ▼                          ▼
 ┌──────────────────────┐   ┌──────────────────────┐   ┌──────────────────────┐
 │ Fulfillment Optimizer│   │ ETA Prediction Svc   │   │ Notification Worker  │
 │ (Python / OR-Tools / │   │ (Python / FastAPI /  │   │ (Java / Reactive)    │
 │  Greedy Heuristic)   │   │  Scikit-Learn Model) │   │                      │
 └──────────┬───────────┘   └──────────┬───────────┘   └──────────┬───────────┘
            │                          │                          │
            └──────────────────────────┼──────────────────────────┘
                                       ▼
                       ┌────────────────────────────────┐
                       │ Redis 7.x Distributed Cluster  │
                       │ - Distributed Mutex (Redlock)  │
                       │ - Idempotency State Store      │
                       │ - SKU Inventory Read Cache     │
                       └────────────────────────────────┘
```

---

## 4. Domain Entities & Database Design

### 4.1 Storage Strategy & Simulated Sharding
To simulate planetary-scale relational order distribution, the **Order Database** is partitioned into $N = 3$ virtual shards using consistent hash routing:
$$\text{Shard ID} = \text{hash}(\text{customer\_id}) \pmod 3$$

The application utilizes a `ShardRoutingDataSource` determining dynamic connections per thread transaction.

```
                    ┌─────────────────────────┐
                    │ Shard Routing Resolver  │
                    │  (MurmurHash3 / CRC32)  │
                    └────────────┬────────────┘
                                 │
             ┌───────────────────┼───────────────────┐
             ▼                   ▼                   ▼
    ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
    │   OrderShard_0  │ │   OrderShard_1  │ │   OrderShard_2  │
    │  Hash Range 0   │ │  Hash Range 1   │ │  Hash Range 2   │
    └─────────────────┘ └─────────────────┘ └─────────────────┘
```

### 4.2 Relational Data DDL

#### Sharded Order Table (`orders`)
```sql
CREATE TABLE orders (
    order_id VARCHAR(64) PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    customer_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    total_amount NUMERIC(12, 2) NOT NULL,
    shipping_address_json JSONB NOT NULL,
    destination_pincode VARCHAR(16) NOT NULL,
    assigned_fc_id VARCHAR(32),
    estimated_delivery_at TIMESTAMPTZ,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_customer ON orders (customer_id, created_at DESC);
CREATE INDEX idx_orders_status ON orders (status);
```

#### Order Items Table (`order_items`)
```sql
CREATE TABLE order_items (
    item_id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    sku_id VARCHAR(64) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 2) NOT NULL
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
```

#### Inventory Table (`inventory_levels`)
```sql
CREATE TABLE fulfillment_centers (
    fc_id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    location_lat DOUBLE PRECISION NOT NULL,
    location_lon DOUBLE PRECISION NOT NULL,
    pincode VARCHAR(16) NOT NULL,
    max_daily_capacity INT NOT NULL,
    current_load INT NOT NULL DEFAULT 0
);

CREATE TABLE inventory_levels (
    fc_id VARCHAR(32) NOT NULL REFERENCES fulfillment_centers(fc_id),
    sku_id VARCHAR(64) NOT NULL,
    available_quantity INT NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    safety_stock INT NOT NULL DEFAULT 5,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (fc_id, sku_id)
);

CREATE INDEX idx_inventory_sku ON inventory_levels (sku_id);
```

#### Outbox Table for Guaranteed Event Publishing (`transactional_outbox`)
```sql
CREATE TABLE transactional_outbox (
    outbox_id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON transactional_outbox (status, created_at);
```

---

## 5. Event-Driven Messaging Fabric (Apache Kafka)

### 5.1 Topic Topology & Partitioning

| Topic Name | Partitions | Replication | Partition Key | Retention | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `order.events.created` | 8 | 3 (in prod) | `order_id` | 7 days | Published upon order ingestion & DB commit |
| `inventory.events.reserved` | 8 | 3 | `order_id` | 7 days | Published when stock is locked at an FC |
| `fulfillment.events.assigned` | 8 | 3 | `order_id` | 7 days | Published when optimizer selects optimal FC |
| `eta.events.predicted` | 8 | 3 | `order_id` | 7 days | Published by prediction inference workers |
| `order.events.failed` | 4 | 3 | `order_id` | 14 days | Published when unrecoverable failure occurs |
| `order.events.dlq` | 4 | 3 | `order_id` | 30 days | Dead Letter Queue after max retries |

### 5.2 Event Payload Specifications (JSON Schema)

#### `OrderCreatedEvent`
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "OrderCreatedEvent",
  "type": "object",
  "properties": {
    "eventId": { "type": "string", "format": "uuid" },
    "orderId": { "type": "string" },
    "customerId": { "type": "string" },
    "destinationPincode": { "type": "string" },
    "items": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "skuId": { "type": "string" },
          "quantity": { "type": "integer", "minimum": 1 }
        },
        "required": ["skuId", "quantity"]
      }
    },
    "timestamp": { "type": "string", "format": "date-time" }
  },
  "required": ["eventId", "orderId", "customerId", "destinationPincode", "items", "timestamp"]
}
```

---

## 6. Algorithmic Optimization & Distributed Prediction

### 6.1 Fulfillment Allocation Optimization
When an order demands item set $\mathcal{I} = \{(s, q_s)\}$, multiple fulfillment centers $\mathcal{F} = \{f_1, f_2, \dots, f_m\}$ possess varying inventory levels $A_{f, s}$. The platform formulates fulfillment as a multi-criteria optimization problem:

#### Objective Function
$$\min \sum_{f \in \mathcal{F}} \sum_{s \in \mathcal{I}} \left( \alpha \cdot C_{\text{ship}}(f, \text{dest}) + \beta \cdot T_{\text{transit}}(f, \text{dest}) + \gamma \cdot \frac{L_f}{K_f} \right) \cdot x_{f, s}$$

Where:
- $x_{f, s} \in \mathbb{Z}_{\ge 0}$: Quantity of SKU $s$ fulfilled by warehouse $f$.
- $C_{\text{ship}}(f, \text{dest})$: Distance-based shipping cost.
- $T_{\text{transit}}(f, \text{dest})$: Expected delivery hours.
- $L_f / K_f$: Current utilization ratio (active load / max capacity) of warehouse $f$.
- $\alpha, \beta, \gamma$: Weighting parameters tuning cost vs speed vs warehouse congestion.

#### Constraints
1. **Demand Satisfaction:** $\sum_{f \in \mathcal{F}} x_{f, s} = q_s \quad \forall s \in \mathcal{I}$
2. **Stock Availability:** $x_{f, s} \le A_{f, s} \quad \forall f \in \mathcal{F}, \forall s \in \mathcal{I}$
3. **Split Shipment Penalty:** Additional cost incurred if $|\{f \mid \sum_s x_{f,s} > 0\}| > 1$.

#### Evaluated Algorithms
- **Greedy Baseline:** Proximity-first heuristic allocating highest-stock nearest neighbor.
- **Linear Programming (LP/MIP):** Mixed-integer linear programming solved using Google OR-Tools.
- **Benchmarking Metric:** Total fulfillment cost, average ETA hours, and split-shipment percentage.

### 6.2 Distributed ETA Prediction Service
- **Model:** Gradient Boosted Regressor (XGBoost / LightGBM) trained on origin-destination pairs, order weight, warehouse dispatch backlog, historical carrier transit, weather index, and time-of-day.
- **Distributed Inference Workers:** Horizontally scaled Python/FastAPI workers attached to consumer group `eta-prediction-group`. Partitions are load-balanced across workers to sustain >2,500 predictions/sec.

---

## 7. Distributed Concurrency, Fault Tolerance & Caching

### 7.1 Distributed Locking (Redis Redlock Pattern)
To eliminate overselling during high-concurrency reservation across multi-node inventory services:
```
Key: lock:inventory:{sku_id}
TTL: 2000 ms
Value: UUID (owner thread identifier)
```
- Ingress thread attempts non-blocking acquire via `SET lock:inventory:{sku} {uuid} NX PX 2000`.
- If lock is held, thread engages exponential backoff with jitter (max 3 retries).
- Atomic release via Lua script validating ownership UUID.

### 7.2 Idempotency Engine
To prevent duplicate order generation upon client network timeouts and retries:
1. Client generates `Idempotency-Key: <UUID>`.
2. API Gateway checks Redis key `idemp:{key}`:
   - **Case 1 (In Flight):** Value is `IN_PROGRESS` $\to$ return `409 Conflict` or poll wait.
   - **Case 2 (Completed):** Value contains cached response payload $\to$ return cached `200 OK` instantly with header `X-Cache-Lookup: HIT`.
   - **Case 3 (New):** Atomically write `SET idemp:{key} IN_PROGRESS NX EX 120`. Proceed to process order. Update key with final JSON payload upon successful commit.

### 7.3 Circuit Breakers & Fallback Chains (Resilience4j)
- **Failure Threshold:** 50% failures in a rolling window of 20 requests opens the circuit.
- **Wait Duration in Open State:** 5000 ms.
- **Fallback Action:** Order status transitions to `PENDING_INVENTORY_VERIFICATION` and publishes to Kafka retry topic without rejecting customer HTTP request.

---

## 8. OpenSearch Indexing & Search Topology

### 8.1 Product & Order Indices
- **Index `scalefulfill-orders`:** Sharded across 3 primary nodes, 1 replica.
  - Fields: `order_id` (keyword), `customer_id` (keyword), `status` (keyword), `destination_pincode` (keyword), `items` (nested), `created_at` (date).
  - Search Capabilities: Order tracking, status aggregations, customer history search with sub-30ms query latency.
- **Index `scalefulfill-products`:**
  - Tokenizers: Edge-ngram for search-as-you-type autocomplete.
  - Faceted filters: Category, brand, price range, stock availability.

---

## 9. Observability & Telemetry

### 9.1 Core Metrics (Prometheus)
- `scalefulfill_orders_received_total{status="SUCCESS|RETRY|FAILED"}`
- `scalefulfill_order_processing_duration_seconds{quantile="0.5|0.95|0.99"}`
- `scalefulfill_kafka_consumer_lag{topic, partition, consumergroup}`
- `scalefulfill_inventory_lock_acquisition_duration_ms`
- `scalefulfill_prediction_latency_ms`

### 9.2 Operations Copilot
An autonomous diagnostic agent that consumes Prometheus metrics and OpenSearch order indices via REST tool endpoints to answer real-time incident queries (e.g., *"Why did Bangalore FC dispatch latency spike by 40% in the last 15 minutes?"*).

---

## 10. Phase-by-Phase Implementation Roadmap

| Phase | Milestone Name | Core Deliverables | Verification Exit Criteria |
| :---: | :--- | :--- | :--- |
| **01** | **Monolith Ingress Core** | Order & Inventory domain models, PostgreSQL, Spring Boot REST, unit/integration tests | End-to-end order placement with DB persistence & test coverage $\ge 85\%$ |
| **02** | **Service Decomposition** | Split into Order Service, Inventory Service, and API Gateway | Independent dockerized services communicating over HTTP |
| **03** | **Kafka Event Fabric** | Outbox pattern, Kafka cluster, asynchronous event producers and consumers | Zero lost events under simulated producer node crash |
| **04** | **Distributed Storage & Redis** | 3-way hash sharding for PostgreSQL, Redis caching & distributed locks | Verified concurrent stock reservations without overselling |
| **05** | **Search & Indexing Engine** | OpenSearch sync pipeline, faceted and full-text order/product search | $P_{95} \le 30\text{ms}$ query latency across 100k indexed records |
| **06** | **Distributed ETA Prediction** | Python FastAPI worker cluster, ML inference pipeline over Kafka | Distributed prediction workload partitioned across 4 workers |
| **07** | **Optimization Engine** | Greedy vs Linear Programming (OR-Tools) fulfillment allocator | Benchmarking report comparing shipping costs & runtime |
| **08** | **Resilience & Fault Tolerance** | Resilience4j circuit breakers, rate limiting, DLQ auto-replayer | System sustains 50% simulated service outage without data loss |
| **09** | **Full Observability** | Prometheus exporter, Grafana dashboards, OpenTelemetry traces | Real-time monitoring dashboard showing live throughput & lag |
| **10** | **Operations Copilot & Frontend** | React Operator UI + AI Diagnostic Tool layer | Interactive UI for monitoring, order placement, and incident query |
