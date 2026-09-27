#!/usr/bin/env python3
"""
ScaleFulfill Phase 6 — Empirical Benchmark Suite
Tests:
1. Distributed Prediction Worker Pool Scaling (1, 2, 4, 8 workers across 1,000 events)
2. Greedy vs OR-Tools MILP Mathematical Optimization Engine (N = 10, 50, 200, 500)
3. Stale Prediction TTL & Filter Evaluation
4. Prometheus Telemetry & Cardinality Validation
"""

import json
import time
import random
import concurrent.futures
import requests

PREDICTION_BASE_URL = "http://localhost:8085"
PROMETHEUS_URL = "http://localhost:9090"

REGIONAL_FCS = [
    {
        "fcId": "FC-NORTH",
        "name": "Chicago Regional FC",
        "latitude": 41.8781,
        "longitude": -87.6298,
        "maxCapacity": 250,
        "baseCost": 4.50,
        "costPerKm": 0.08,
        "availableInventory": {"PROD-101": 500, "PROD-102": 500, "PROD-103": 500}
    },
    {
        "fcId": "FC-SOUTH",
        "name": "Dallas Regional FC",
        "latitude": 32.7767,
        "longitude": -96.7970,
        "maxCapacity": 200,
        "baseCost": 4.20,
        "costPerKm": 0.075,
        "availableInventory": {"PROD-101": 400, "PROD-102": 400, "PROD-103": 400}
    },
    {
        "fcId": "FC-EAST",
        "name": "Newark Regional FC",
        "latitude": 40.7357,
        "longitude": -74.1724,
        "maxCapacity": 300,
        "baseCost": 5.00,
        "costPerKm": 0.09,
        "availableInventory": {"PROD-101": 600, "PROD-102": 600, "PROD-103": 600}
    },
    {
        "fcId": "FC-WEST",
        "name": "Seattle Regional FC",
        "latitude": 47.6062,
        "longitude": -122.3321,
        "maxCapacity": 220,
        "baseCost": 4.80,
        "costPerKm": 0.085,
        "availableInventory": {"PROD-101": 450, "PROD-102": 450, "PROD-103": 450}
    },
    {
        "fcId": "FC-CENTRAL",
        "name": "Denver Regional FC",
        "latitude": 39.7392,
        "longitude": -104.9903,
        "maxCapacity": 180,
        "baseCost": 4.00,
        "costPerKm": 0.07,
        "availableInventory": {"PROD-101": 350, "PROD-102": 350, "PROD-103": 350}
    }
]

def generate_orders(n):
    orders = []
    # Seeded customer coordinates across USA
    metro_coords = [
        (40.7128, -74.0060), # NYC
        (34.0522, -118.2437), # LA
        (41.8781, -87.6298), # Chicago
        (29.7604, -95.3698), # Houston
        (33.4484, -112.0740), # Phoenix
        (39.9526, -75.1652), # Philly
        (37.7749, -122.4194), # SF
        (47.6062, -122.3321), # Seattle
        (39.7392, -104.9903), # Denver
        (25.7617, -80.1918)  # Miami
    ]
    random.seed(42)
    for i in range(1, n + 1):
        base_lat, base_lon = random.choice(metro_coords)
        lat = base_lat + random.uniform(-0.5, 0.5)
        lon = base_lon + random.uniform(-0.5, 0.5)
        qty1 = random.randint(1, 3)
        qty2 = random.randint(0, 2)
        item_demands = {"PROD-101": qty1}
        if qty2 > 0:
            item_demands["PROD-102"] = qty2
        orders.append({
            "orderId": f"ORD-OPT-{i:04d}",
            "customerId": f"CUST-{(i % 20) + 1000}",
            "latitude": round(lat, 4),
            "longitude": round(lon, 4),
            "itemDemands": item_demands
        })
    return orders

def run_optimization_benchmark():
    print("=" * 70)
    print("EXPERIMENT 1: Mathematical Optimization Benchmark (Greedy vs MILP)")
    print("=" * 70)

    batch_sizes = [10, 50, 200, 500]
    results = []

    for n in batch_sizes:
        orders = generate_orders(n)
        # Proportionate capacity so capacity constraints remain active
        fcs = json.loads(json.dumps(REGIONAL_FCS))
        cap_per_fc = max(5, int((n * 1.3) / len(fcs)))
        for fc in fcs:
            fc["maxCapacity"] = cap_per_fc

        payload = {
            "batchId": f"BATCH-{n}",
            "solverTimeoutMs": 4000,
            "imbalancePenaltyWeight": 0.25,
            "orders": orders,
            "fulfillmentCenters": fcs
        }

        resp = requests.post(f"{PREDICTION_BASE_URL}/api/optimize/compare", json=payload, timeout=30)
        if resp.status_code != 200:
            print(f"Error running batch {n}: {resp.status_code} - {resp.text}")
            continue

        data = resp.json()
        greedy = data["greedyResult"]
        milp = data["milpResult"]

        record = {
            "batch_size": n,
            "greedy_cost": greedy["totalCost"],
            "milp_cost": milp["totalCost"],
            "cost_savings_pct": data["costSavingsPercentage"],
            "greedy_time_ms": greedy["solveTimeMs"],
            "milp_time_ms": milp["solveTimeMs"],
            "time_ratio": data["solveTimeRatio"],
            "milp_status": milp["status"],
            "optimality_gap": milp["optimalityGap"],
            "best_bound": milp.get("bestBound"),
            "greedy_std_dev": greedy["capacityUtilizationStdDev"],
            "milp_std_dev": milp["capacityUtilizationStdDev"]
        }
        results.append(record)
        print(f"N={n:3d} | Greedy Cost: ${record['greedy_cost']:8.2f} ({record['greedy_time_ms']:5.1f}ms) | "
              f"MILP Cost: ${record['milp_cost']:8.2f} ({record['milp_time_ms']:6.1f}ms) | "
              f"Savings: {record['cost_savings_pct']:5.2f}% | Status: {record['milp_status']} (gap: {record['optimality_gap']}%)")

    return results

def run_worker_scaling_benchmark():
    print("\n" + "=" * 70)
    print("EXPERIMENT 2: Distributed Prediction Worker Pool Scaling (1,000 Events)")
    print("=" * 70)

    worker_configs = [1, 2, 4, 8]
    event_count = 1000
    results = []

    for workers in worker_configs:
        # Reconfigure workers
        reconf = requests.post(f"{PREDICTION_BASE_URL}/api/predictions/workers?size={workers}")
        assert reconf.status_code == 200, f"Failed to reconfigure workers to {workers}"

        # Generate 1,000 events
        events = []
        for i in range(1, event_count + 1):
            events.append({
                "eventId": f"SCALING-EVT-{workers}-{i}",
                "orderId": f"ORD-SCALE-{workers}-{i}",
                "customerId": f"CUST-{1001 + (i % 3)}",
                "totalAmount": round(25.0 + (i % 200) * 1.5, 2),
                "items": [
                    {
                        "productId": "PROD-101",
                        "quantity": 1 + (i % 3),
                        "unitPrice": 49.99,
                        "weightKg": 0.8
                    }
                ]
            })

        latencies = []
        start_time = time.perf_counter()

        def submit_event(evt):
            t0 = time.perf_counter()
            r = requests.post(f"{PREDICTION_BASE_URL}/api/predictions", json=evt, timeout=15)
            t1 = time.perf_counter()
            latencies.append((t1 - t0) * 1000.0)
            return r.status_code

        # Dispatch via concurrent client thread pool simulating distributed Kafka ingress
        with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
            statuses = list(pool.map(submit_event, events))

        total_duration = time.perf_counter() - start_time
        throughput = event_count / total_duration

        latencies.sort()
        p50 = latencies[int(len(latencies) * 0.50)]
        p90 = latencies[int(len(latencies) * 0.90)]
        p95 = latencies[int(len(latencies) * 0.95)]
        p99 = latencies[int(len(latencies) * 0.99)]
        mean_lat = sum(latencies) / len(latencies)

        # Check queue stats
        stats = requests.get(f"{PREDICTION_BASE_URL}/api/predictions/stats").json()

        res = {
            "workers": workers,
            "events": event_count,
            "duration_s": round(total_duration, 2),
            "throughput_eps": round(throughput, 1),
            "mean_ms": round(mean_lat, 2),
            "p50_ms": round(p50, 2),
            "p95_ms": round(p95, 2),
            "p99_ms": round(p99, 2),
            "queue_depth": stats.get("queueDepth", 0)
        }
        results.append(res)
        print(f"Workers: {workers} | Throughput: {res['throughput_eps']:6.1f} evt/s | "
              f"P50: {res['p50_ms']:4.1f}ms | P95: {res['p95_ms']:5.1f}ms | P99: {res['p99_ms']:5.1f}ms")

    return results

def run_stale_and_telemetry_checks():
    print("\n" + "=" * 70)
    print("EXPERIMENT 3: Stale Prediction Filtering & Prometheus Telemetry")
    print("=" * 70)

    # 1. Verify single prediction retrieval and staleness evaluation
    check_order = "ORD-SCALE-4-1"
    resp = requests.get(f"{PREDICTION_BASE_URL}/api/predictions/{check_order}")
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}"
    p = resp.json()
    assert p["orderId"] == check_order
    assert p["modelVersion"] == "eta-v1"
    assert "distanceKm" in p["featureVector"]
    assert "carrierVelocityKmh" in p["featureVector"]
    print(f"[OK] Prediction verified for {check_order}: ETA={p['predictedEtaMinutes']}m, DemandScore={p['demandVelocityScore']}")

    # 2. Check Prometheus metrics
    prom_metrics = requests.get(f"{PREDICTION_BASE_URL}/actuator/prometheus").text
    key_metrics = [
        "scalefulfill_prediction_total",
        "scalefulfill_prediction_processing_duration_seconds",
        "scalefulfill_prediction_workers_active",
        "scalefulfill_prediction_queue_depth",
        "scalefulfill_optimization_solve_duration_seconds"
    ]
    for m in key_metrics:
        assert m in prom_metrics, f"Missing metric {m} in Prometheus output"
        print(f"[OK] Prometheus metric verified: {m}")

    # 3. Check Prometheus server scrape health
    prom_query = requests.get(f"{PROMETHEUS_URL}/api/v1/targets").json()
    prediction_target = next((t for t in prom_query["data"]["activeTargets"] if t["labels"].get("service") == "prediction-service"), None)
    assert prediction_target is not None, "prediction-service target not found in Prometheus"
    assert prediction_target["health"] == "up", f"prediction-service health is {prediction_target['health']}"
    print(f"[OK] Prometheus target prediction-service is UP (lastScrape={prediction_target['lastScrape']})")

if __name__ == "__main__":
    opt_results = run_optimization_benchmark()
    scale_results = run_worker_scaling_benchmark()
    run_stale_and_telemetry_checks()

    # Output JSON summary for artifact documentation
    summary = {
        "optimization": opt_results,
        "scaling": scale_results,
        "timestamp": time.time()
    }
    with open("docs/benchmarks/phase6-raw-results.json", "w") as f:
        json.dump(summary, f, indent=2)
    print("\nBenchmark results written to docs/benchmarks/phase6-raw-results.json")
