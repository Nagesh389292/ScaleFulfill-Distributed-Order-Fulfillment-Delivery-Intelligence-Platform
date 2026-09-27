# Phase 2 Distributed Synchronous Benchmark Report

**Architecture:** Client -> API Gateway (8080) -> Order Service (8081) -> Inventory Service (8082)
**Date:** 2026-09-27 18:58:01

## Benchmark Results Summary

| Benchmark Tier | Requests | Concurrency | Duration (s) | Throughput (req/s) | P50 (ms) | P95 (ms) | P99 (ms) | Success (201) | Conflicts (409) | Rate Limited (429) | Errors |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Tier 1 (Single Client)** | 100 | 1 | 5.813 | **17.2** | 57.09 | 78.95 | 132.75 | 100 | 0 | 0 | 0.0% |
| **Tier 2 (Moderate Concurrency)** | 500 | 10 | 11.531 | **43.36** | 192.94 | 406.89 | 2170.67 | 244 | 256 | 0 | 0.0% |
| **Tier 3 (High Concurrency)** | 1000 | 25 | 14.523 | **68.86** | 339.41 | 575.86 | 674.45 | 500 | 500 | 0 | 0.0% |


## Comparison: Phase 1 Monolith vs Phase 2 Distributed Synchronous

| Metric | Phase 1 Monolith | Phase 2 Distributed Synchronous | Architectural Explanation |
| :--- | :--- | :--- | :--- |
| **Tier 1 P50 Latency** | 32.0 ms | 57.09 ms | Network hop overhead (Gateway + Inter-service HTTP serialization) |
| **Tier 2 P95 Latency** | 46.9 ms | 406.89 ms | Compounding thread latency across downstream boundaries |
| **Tier 3 Throughput** | 512.6 req/s | 68.86 req/s | Synchronous blocking across distributed microservice tiers |
| **Database Coupling** | Tight (shared DB) | Completely Isolated (order_db & inventory_db) | Eliminates shared DB connection starvation and table lock contention |
| **Edge Protection** | None | Token Bucket Rate Limiting (429) | Prevents upstream DDoS bursts from reaching backend services |
| **Downstream Outage** | Process crash | Circuit Breaker Fast-Fail (503 / Fallback) | Protects upstream order service thread pools from exhaustion |

## Critical Takeaway for Phase 3

Phase 2 successfully accomplished service and database decoupling. However, synchronous HTTP coupling introduces compounding tail latencies and forces the Order Service to block on downstream inventory availability. This provides the exact empirical rationale for introducing **Apache Kafka event-driven choreography and the Transactional Outbox pattern in Phase 3**.
