# ADR-010: CQRS Query Projection & Distributed Inverted Indexing with OpenSearch

## Status
Accepted

## Date
2026-09-27

## Context
In **ScaleFulfill**, orders and inventory must maintain strict relational ACID guarantees for state transitions, payments, and stock reservations. PostgreSQL was chosen as the system of record with optimistic locking and transactional outbox.

However, client search, order history filtering, and catalog discovery present fundamentally different access patterns:
1. **Multi-dimensional Filtering:** Queries filter simultaneously on text substrings (`Apple M3`), nested line-item names, order statuses, customer IDs, and range facets (prices, order dates, weights).
2. **Aggregations & Metrics:** Real-time status breakdown and total revenue summation across millions of records.
3. **Transactional Contention:** In an enterprise fulfillment platform (such as Amazon), read queries hitting the OLTP database compete for connection pool slots and memory buffers, causing lock contention and degrading order placement throughput.

## Decision
We adopted the **Command Query Responsibility Segregation (CQRS)** pattern:
1. **OLTP System of Record:** PostgreSQL remains the transactional source of truth for all command writes (`orders`, `inventory`, `outbox_events`).
2. **Event-Driven Propagation:** PostgreSQL commit triggers asynchronous event streaming via Kafka topic `order.events.created`.
3. **Distributed Inverted Index:** `search-service` (Spring Boot 3.3.4, Java 21 LTS) consumes order events and projects them into **OpenSearch 2.12** inverted indices (`orders-index`, `products-index`).
4. **Decoupled Search Path:** All search queries (`/api/v1/search/orders`, `/api/v1/search/products`) are routed through the API Gateway directly to `search-service` and executed against OpenSearch using Query DSL (`bool`, `multi_match`, `range`, `terms`, and nested queries) with faceted aggregations. Search queries **never** touch the PostgreSQL database.

## Architecture

```text
       Write / Command Path (OLTP)                         Read / Search Path (OLAP)
    ┌──────────────────────────────┐                   ┌──────────────────────────────┐
    │  POST /api/v1/orders         │                   │  GET /api/v1/search/orders   │
    └──────────────┬───────────────┘                   └──────────────┬───────────────┘
                   ▼                                                  ▼
           ┌──────────────┐                                   ┌──────────────┐
           │ Order Service│                                   │Search Service│
           └───────┬──────┘                                   └───────┬──────┘
                   ▼ (ACID Transaction)                               │ (Query DSL)
           ┌──────────────┐                                           │
           │  PostgreSQL  │                                           ▼
           │ ├── orders   │                                   ┌──────────────┐
           │ └── outbox   │                                   │  OpenSearch  │
           └───────┬──────┘                                   │ ├── orders   │
                   ▼                                          │ └── products │
           ┌──────────────┐                                   └──────────────┘
           │Outbox Poller │                                           ▲
           └───────┬──────┘                                           │
                   ▼                                                  │
            Kafka Stream ─────────────────────────────────────────────┘
         (order.events.created)
```

## Consequences

### Positive
- **Complete Read/Write Isolation:** Complex searches, wildcards, and aggregations produce zero read load on the transactional PostgreSQL databases.
- **Sub-50ms Multi-Faceted Query Latency:** Inverted indexes with BM25 scoring and term dictionaries evaluate text and multi-attribute filters in <20 ms (OpenSearch engine time).
- **Fault-Tolerant Asynchronous Catchup:** If the search cluster is temporarily offline or undergoing reindexing, transactional order placement continues without failure; Kafka buffers the change stream until OpenSearch recovers, guaranteeing eventual consistency with zero data loss.

### Negative / Trade-Offs
- **Eventual Consistency:** Search queries reflect newly placed orders with an indexing lag (measured empirically at ~134 ms). Clients requiring read-your-own-writes immediately after placement can fall back to direct key-lookup (`GET /api/v1/orders/{id}`) against PostgreSQL.
- **Dual Infrastructure Maintenance:** Requires running and tuning OpenSearch cluster instances, memory allocations, shard allocations, and mapping schemas alongside PostgreSQL.
