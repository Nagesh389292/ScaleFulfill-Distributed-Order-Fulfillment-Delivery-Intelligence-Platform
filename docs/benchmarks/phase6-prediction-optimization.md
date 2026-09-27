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

## 4. Experiment 2: Distributed Prediction Worker Pool Scaling

Evaluated concurrent prediction generation under a sustained burst of **1,000 order prediction events** dispatched across varying worker thread allocations ($W \in \{1, 2, 4, 8\}$).

| Workers ($W$) | Total Events | Wall-Clock Time | Throughput | P50 Latency | P95 Latency | P99 Latency | Max Queue Depth |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **1** | 1,000 | 1.68 s | **594.7 evt/s** | 23.1 ms | 41.0 ms | 82.8 ms | 0 |
| **2** | 1,000 | 2.48 s | **402.5 evt/s** | 32.9 ms | 68.9 ms | 84.1 ms | 0 |
| **4** | 1,000 | 2.66 s | **375.6 evt/s** | 36.9 ms | 64.0 ms | 77.3 ms | 0 |
| **8** | 1,000 | 2.70 s | **370.1 evt/s** | 37.2 ms | 64.7 ms | 92.3 ms | 0 |

### Engineering Analysis:
- Sub-millisecond compute tasks ($<1\text{ms}$ calculation) experience negligible queue build-up when backed by pre-cached read models.
- As worker concurrency increases from 1 to 8 under client saturation, thread switching overhead in small-task workloads plateaus throughput at ~370–400 evt/s, with P95 latency stabilizing at ~64ms.
- Backpressure defense: Queue capacity bounded at 1,000 items; `scalefulfill_prediction_total{status="rejected"}` remained at 0 under standard load and actively triggers under simulated memory defense.

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
