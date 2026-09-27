# ADR-002: Customer-Based Hash Sharding for Relational Order Storage

## Status
Accepted

## Context
As order volume scales beyond hundreds of millions of records, a single monolithic PostgreSQL database table faces storage limitations, index bloat (B-tree index depth causing degraded insert latency), and connection pool saturation.

We need a horizontal partition/sharding strategy that:
1. Allows horizontal scaling across multiple database instances.
2. Keeps transactional operations efficient for the most frequent access pattern: customer order placement, order status queries, and customer order history.
3. Minimizes cross-shard distributed transactions (2PC), which degrade throughput and availability.

## Decision
We implement **Application-Level Hash Sharding** partitioned by `customer_id` across $N = 3$ PostgreSQL shards:
$$\text{Shard ID} = |\text{MurmurHash3}(\text{customer\_id})| \pmod N$$

1. **Routing Mechanism:** The Spring Boot backend implements an abstract `ShardRoutingDataSource` that dynamically resolves target database connection pools on a per-transaction basis using the thread-local context populated from the request's `customer_id`.
2. **Order Lookup by ID:** For queries that only provide `order_id` (such as webhook callbacks or carrier updates), a global Redis lookup index maps `order_id -> customer_id` (or embed `shard_id` directly in the structured order identifier: `ORD-<shard_id>-<uuid>`).
3. **Data Integrity:** Primary keys and foreign keys (`orders` and `order_items`) reside within the same physical shard, preserving local ACID transaction guarantees without distributed two-phase commits.

## Consequences
### Positive
- Read and write workloads for a customer execute entirely within a single shard, retaining local ACID guarantees.
- B-tree index sizes on each database node remain compact and memory-resident.
- Avoids distributed locks across databases for primary order workflows.

### Negative / Trade-offs
- Cross-customer analytical queries (e.g., aggregate sales across all users) cannot perform simple joins and must query OpenSearch or an offline data lake.
- Resharding (increasing $N$) requires a rebalancing migration strategy (consistent hashing or virtual vnode mapping).
