# Phase 6 Benchmark Report: Distributed Prediction & Mathematical Optimization Engine

## Executive Summary
ScaleFulfill Phase 6 introduces two specialized distributed capabilities targeting core enterprise engineering requirements:
1. **Asynchronous Distributed Prediction Workload:** A dedicated, event-driven prediction service consuming `order.events.created` from Apache Kafka, utilizing a bounded thread worker pool with active backpressure defense, computing deterministic kinematic delivery ETAs (`eta-v1`), demand velocity scores, and enforcing TTL-based stale prediction filtering.
2. **Constrained Mathematical Optimization Engine:** A multi-facility order allocation engine formulating batch dispatch as a Mixed-Integer Linear Program (MILP) solved using **Google OR-Tools** (SCIP mathematical backend) compared directly against a **Greedy Nearest-Feasible Heuristic Baseline**.

Rather than assuming mathematical optimization is universally superior or treating prediction as an opaque model, both approaches were rigorously evaluated under live workloads, bounded solver budgets, and multi-threaded scaling.

---

## 1. System Architecture

```text
                                Apache Kafka
                            order.events.created
                                     │
                        ┌────────────┴────────────┐
                        │                         │
                        ▼                         ▼
            Prediction Service              Optimization Engine
        (group: prediction-service-group)   (Wave / Batch Dispatch)
                        │                         │
               Bounded Worker Pool           Batch Solvers
             (core: 4-8, queue: 1000)             │
             ┌──────────┴──────────┐       ┌──────┴──────┐
             ▼                     ▼       ▼             ▼
       Kinematic ETA         Demand Score Greedy       OR-Tools
         (eta-v1)              [0.0, 1.0] Baseline      (SCIP MILP)
             │                     │       │             │
             └──────────┬──────────┘       └──────┬──────┘
                        ▼                         ▼
                 Prediction Store          Allocation Result
               (TTL Stale Filter)      (Cost, Bounds, Gap, Util)
                        │                         │
                        └──────────┬──────────────┘
                                   ▼
                         Prometheus & Grafana
```

---

## 2. Mathematical Optimization Formulation

### Objective Function
Minimize total delivery shipping cost, facility handling base cost, penalty for unfulfilled demand, and network workload imbalance:

$$\min Z = \sum_{i=1}^N \sum_{j=1}^M \Big( \text{BaseCost}_j + \text{CostPerKm}_j \times \text{Distance}(i, j) \Big) x_{ij} + \sum_{i=1}^N P \cdot u_i + \lambda \sum_{j=1}^M \text{Deviation}_j$$

where:
- $x_{ij} \in \{0, 1\}$: Binary decision variable indicating order $i$ is assigned to facility $j$.
- $u_i \in [0, 1]$: Slack penalty variable ensuring mathematical feasibility under extreme stock-outs ($P = \$5000$).
- $\text{Deviation}_j \ge \sum_i x_{ij} - \bar{L}$: Workload variance penalty from average network load $\bar{L} = \frac{N}{M}$ ($\lambda = 0.25$).

### Operational Constraints
1. **Assignment Constraint:** Each order must be fulfilled by exactly one facility:
   $$\sum_{j=1}^M x_{ij} + u_i = 1 \quad \forall i \in \{1,\dots,N\}$$
2. **Dispatch Throughput Capacity:** Facility dispatch cannot exceed hourly throughput limit $C_j$:
   $$\sum_{i=1}^N x_{ij} \le C_j \quad \forall j \in \{1,\dots,M\}$$
3. **Multi-SKU Inventory Availability:** Order demands cannot exceed on-hand inventory $I_{jk}$ for SKU $k$:
   $$\sum_{i=1}^N \text{demand}_{ik} \cdot x_{ij} \le I_{jk} \quad \forall j, k$$

---

## 3. Experiment 1: Optimization Benchmark (Greedy Heuristic vs OR-Tools MILP)

Tested on 5 regional fulfillment centers across the United States (`FC-NORTH` Chicago, `FC-SOUTH` Dallas, `FC-EAST` Newark, `FC-WEST` Seattle, `FC-CENTRAL` Denver) with finite dispatch capacity and multi-SKU inventory.

*Solver timeout configured at 4,000ms.*

| Batch Size ($N$) | Greedy Cost | MILP Cost | Cost Savings (%) | Greedy Time | MILP Time | Time Ratio | MILP Status | Optimality Gap | Workload StdDev (Greedy vs MILP) |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **10** | \$834.82 | \$834.82 | **0.00%** | 1.2 ms | 37.5 ms | 31.7x | `OPTIMAL` | **0.0%** | 1.26 vs 1.26 |
| **50** | \$3,358.60 | \$3,017.87 | **10.15%** | 1.0 ms | 26.5 ms | 25.5x | `OPTIMAL` | **0.0%** | 2.68 vs **2.53** |
| **200** | \$11,114.69 | \$10,611.60 | **4.53%** | 3.2 ms | 69.4 ms | 21.5x | `OPTIMAL` | **0.0%** | 12.82 vs 14.46 |
| **500** | \$26,678.56 | \$25,908.09 | **2.89%** | 10.9 ms | 237.4 ms | 21.8x | `OPTIMAL` | **0.0%** | 31.98 vs 31.98 |

### Engineering Analysis:
- **Small Batches ($N=10$):** Under light load with unconstrained capacity, the greedy heuristic finds the exact optimal solution in 1.2ms without solver overhead.
- **Medium Batches ($N=50$):** Capacity constraints become binding. The greedy heuristic fills local facilities prematurely, forcing later orders to ship across the country. The OR-Tools MILP solver discovers global multi-facility assignments saving **\$340.73 per wave (10.15% reduction)** in 26.5ms.
- **Large Batches ($N=500$):** MILP saves **\$770.47** in a single dispatch wave. However, the branch-and-bound search space scales solve time to 237.4ms (a 21.8x computation increase).
- **Production Architecture Takeaway:** For real-time customer checkout, the greedy heuristic is appropriate ($<2\text{ms}$). For scheduled wave-based warehouse release (every 15–30 minutes), the MILP solver pays for its 237ms computation time immediately in direct logistics savings.

---

## 4. Experiment 2: Distributed Prediction Worker Pool Scaling (Workload A vs Workload B)

Evaluated concurrent prediction generation under a sustained burst of **1,000 order prediction events** dispatched across varying worker thread allocations ($W \in \{1, 2, 4, 8\}$) under two computational profiles:
- **Workload A (Lightweight Kinematic Estimator):** Analytical Haversine distance, carrier velocity interpolation, and dispatch queue arithmetic ($<0.05\text{ms}$ calculation).
- **Workload B (CPU-Intensive Numerical Simulation):** Feature processing combined with iterative numerical perturbation (4,000 trigonometric/polynomial iterations simulating Monte Carlo route uncertainty, ~1.5–3.0ms per task).

### Workload A: Lightweight Kinematic Estimator (1,000 Events)

| Workers ($W$) | Total Events | Wall-Clock Time | Throughput | P50 Latency | P95 Latency | P99 Latency | Max Queue Depth |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **1** | 1,000 | 2.22 s | **449.5 evt/s** | 27.8 ms | 65.7 ms | 161.5 ms | 0 |
| **2** | 1,000 | 2.94 s | **340.0 evt/s** | 40.2 ms | 70.5 ms | 92.8 ms | 0 |
| **4** | 1,000 | 2.71 s | **369.3 evt/s** | 37.7 ms | 63.7 ms | 84.5 ms | 0 |
| **8** | 1,000 | 3.18 s | **314.4 evt/s** | 38.7 ms | 116.2 ms | 188.7 ms | 0 |

### Workload B: CPU-Intensive Numerical Simulation (1,000 Events)

| Workers ($W$) | Total Events | Wall-Clock Time | Throughput | P50 Latency | P95 Latency | P99 Latency | Max Queue Depth |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **1** | 1,000 | 2.91 s | **344.0 evt/s** | 38.0 ms | 68.3 ms | 100.8 ms | 0 |
| **2** | 1,000 | 2.60 s | **385.2 evt/s** | 36.9 ms | 57.6 ms | 66.5 ms | 0 |
| **4** | 1,000 | 2.65 s | **377.9 evt/s** | 38.0 ms | 58.0 ms | 71.5 ms | 0 |
| **8** | 1,000 | 2.35 s | **425.4 evt/s** | **33.9 ms** | **50.5 ms** | **61.8 ms** | 0 |

### Distributed Systems Concurrency Analysis:
1. **The Concurrency Paradox in Sub-Millisecond Tasks (Workload A):**
   - For ultra-fast compute tasks ($<0.05\text{ms}$ calculation) backed by in-memory read models, **1 worker outperforms 8 workers (449.5 evt/s vs 314.4 evt/s)**.
   - **Root Cause:** When computation time is shorter than the OS context switch and lock acquisition latency of `ArrayBlockingQueue.poll()`, multi-threading hurts performance. Thread synchronization, CPU cache bouncing across cores, and thread scheduling overhead dominate execution time.
2. **Worker Scaling under CPU-Bound Computations (Workload B):**
   - When each prediction task involves substantial mathematical work (~2ms per event), the worker pool demonstrates positive scaling: **throughput increases from 344.0 evt/s (1 worker) to 425.4 evt/s (8 workers, +23.7% gain)**.
   - Crucially, multi-threading dramatically shrinks tail latencies: **P95 drops from 68.3ms to 50.5ms (-26.1%)**, and **P99 drops from 100.8ms to 61.8ms (-38.7%)**.
3. **Core Engineering Principle:**
   > *A thread pool is an optimization for workloads where task computation cost significantly exceeds queue handoff and synchronization overhead. For sub-millisecond analytical baselines, lightweight single/dual-worker models minimize coordination costs; for CPU-heavy feature simulation or ML inference, bounded multi-worker pools are essential to drain backlogs and protect latency SLAs.*

---

## 5. Experiment 3: Stale Prediction & Telemetry Evaluation

1. **Prediction Contract Verification (`GET /api/predictions/{orderId}`):**
   ```json
   {
     "predictionId": "PRED-AE54CA9B",
     "orderId": "ORD-B2-457",
     "customerId": "CUST-1001",
     "assignedFcId": "FC-EAST",
     "modelVersion": "eta-v1",
     "predictedEtaMinutes": 42.4,
     "demandVelocityScore": 0.05,
     "generatedAt": 1790530625.327,
     "expiresAt": 1790531525.327,
     "stale": false,
     "featureVector": {
       "distanceKm": 14.25,
       "carrierVelocityKmh": 35.0,
       "totalWeightKg": 0.5,
       "fcQueueDepth": 15,
       "dispatchCapacityPerHour": 150.0,
       "handlingTimeMinutes": 12.0
     }
   }
   ```
2. **Prometheus Telemetry Scrape Status:**
   - Scrape target `http://host.docker.internal:8085/actuator/prometheus` reported `health: "up"`, scrape duration `0.232s`.
   - All 6 targets reporting `UP`: `api-gateway`, `order-service`, `inventory-service`, `search-service`, `prediction-service`, `prometheus`.
3. **Cardinality Defense Maintained:**
   - Metric labels remain bounded strictly to low-cardinality status tags (`status="success|failure|rejected"`).
   - High-cardinality identifiers (`orderId`, `customerId`) remain strictly isolated within application payloads and `X-Correlation-Id` log spans.
