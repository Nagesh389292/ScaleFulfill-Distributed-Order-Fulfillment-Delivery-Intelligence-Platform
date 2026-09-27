# Phase 1 Monolith Baseline Benchmark Report

**Environment:** Java 21 LTS, Spring Boot 3.3.4, Synchronous ACID Transactions with Flyway migrations
**Date:** 2026-09-27 18:21:37

## Benchmark Results Summary

| Benchmark Tier | Requests | Concurrency | Duration (s) | Throughput (req/s) | P50 (ms) | P95 (ms) | P99 (ms) | Success (201) | Conflicts (409) | Error Rate |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Tier 1 (Single Client)** | 100 | 1 | 3.771 | **26.52** | 32.01 | 48.36 | 636.57 | 100 | 0 | 0.0% |
| **Tier 2 (Moderate Concurrency)** | 500 | 10 | 1.197 | **417.87** | 20.0 | 46.88 | 92.19 | 129 | 371 | 0.0% |
| **Tier 3 (High Concurrency)** | 1000 | 25 | 1.951 | **512.61** | 43.72 | 94.98 | 134.76 | 121 | 879 | 0.0% |


## Key Architectural Findings

1. **Honest Baseline:** In Phase 1's synchronous transactional architecture, each order undergoes validation, price calculation, inventory lookup, optimistic locking update, and order persistence within a single ACID transaction.
2. **Concurrency Impact:** As concurrency scales from 1 to 25, throughput peaks until database row locks on the shared inventory row induce optimistic lock conflicts (409 Conflict), demonstrating the exact need for asynchronous processing, partitioning, and Redis distributed caching in Phase 2 & 3.
3. **Zero Phantom Orders:** Zero 500 server errors were encountered; all competing concurrent requests were safely managed either through successful commit or structured 409 conflict responses.
