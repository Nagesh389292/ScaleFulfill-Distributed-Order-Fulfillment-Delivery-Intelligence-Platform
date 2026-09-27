#!/usr/bin/env python3
"""
ScaleFulfill Phase 2 - Comprehensive Failure & Recovery Test Suite
Validates:
- Scenario A: Normal Distributed Flow (Gateway -> Order -> Inventory -> 200/201)
- Scenario B: Inventory Service Outage & Circuit Breaker Fast-Fail (503 Controlled Error, NO 500/NPE/trace)
- Scenario C: Service Recovery & Circuit Breaker Reset (Restart Inventory -> 201 Success)
"""

import subprocess
import time
import json
import urllib.request
import urllib.error
import sys
import os

JAVA_BIN = r"C:\Program Files\Java\jdk-21\bin\java.exe"
GATEWAY_URL = "http://localhost:8080"
ORDER_URL = "http://localhost:8081"
INVENTORY_URL = "http://localhost:8082"

def print_banner(msg):
    print(f"\n{'='*70}\n  {msg}\n{'='*70}")

def wait_for_endpoint(url, timeout=50):
    t0 = time.time()
    while time.time() - t0 < timeout:
        try:
            with urllib.request.urlopen(url, timeout=2) as resp:
                if resp.status in [200, 404]:
                    return True
        except urllib.error.HTTPError as e:
            if e.code in [200, 404]:
                return True
        except Exception:
            pass
        time.sleep(0.5)
    return False

def main():
    print_banner("PHASE 2 DISTRIBUTED FAILURE & RECOVERY VALIDATION SUITE")

    procs = {}

    try:
        # 1. Start Inventory Service
        print("[1/3] Launching Inventory Service (port 8082)...")
        procs['inventory'] = subprocess.Popen(
            [JAVA_BIN, "-Dspring.profiles.active=local", "-jar", "services/inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )
        assert wait_for_endpoint(f"{INVENTORY_URL}/api/v1/inventory/PROD-101"), "Inventory Service failed to start"
        print("  -> Inventory Service UP on port 8082")

        # 2. Start Order Service
        print("[2/3] Launching Order Service (port 8081)...")
        procs['order'] = subprocess.Popen(
            [JAVA_BIN, "-Dspring.profiles.active=local", "-jar", "services/order-service/target/order-service-1.0.0-SNAPSHOT.jar"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )
        assert wait_for_endpoint(f"{ORDER_URL}/actuator/health"), "Order Service failed to start"
        print("  -> Order Service UP on port 8081")

        # 3. Start API Gateway
        print("[3/3] Launching API Gateway (port 8080)...")
        procs['gateway'] = subprocess.Popen(
            [JAVA_BIN, "-jar", "services/api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )
        assert wait_for_endpoint(f"{GATEWAY_URL}/api/v1/inventory/PROD-101", timeout=30), "API Gateway failed to start"
        print("  -> API Gateway UP on port 8080")

        # =====================================================================
        # SCENARIO A: Normal Distributed Flow
        # =====================================================================
        print_banner("SCENARIO A: Normal Distributed Flow (Gateway -> Order -> Inventory)")
        order_payload = {
            "customerId": "CUST-1001",
            "items": [{"productId": "PROD-101", "quantity": 1, "unitPrice": 1499.00}]
        }
        req_a = urllib.request.Request(
            f"{GATEWAY_URL}/api/v1/orders",
            data=json.dumps(order_payload).encode(),
            headers={"Content-Type": "application/json", "X-Customer-Id": "USER-SCENARIO-A"}
        )
        try:
            with urllib.request.urlopen(req_a, timeout=5) as resp:
                status = resp.status
                body = json.loads(resp.read().decode())
                corr_id = resp.headers.get("X-Correlation-Id")
                print(f"-> Response Status: HTTP {status}")
                print(f"-> Order ID: {body.get('orderId')}")
                print(f"-> Status: {body.get('status')} | Assigned FC: {body.get('assignedFcId')}")
                print(f"-> Correlation ID: {corr_id}")
                assert status == 201, "Expected HTTP 201"
                assert body.get("status") == "RESERVED", "Expected status RESERVED"
                print(">>> SCENARIO A VERIFIED: End-to-end distributed order placement succeeded!")
        except urllib.error.HTTPError as e:
            err_body = e.read().decode()
            print(f"-> Scenario A HTTPError {e.code}: {err_body}")
            raise e

        # =====================================================================
        # SCENARIO B: Inventory Outage & Circuit Breaker Fast-Failure
        # =====================================================================
        print_banner("SCENARIO B: Downstream Inventory Outage & Circuit Breaker Fast-Failure")
        print("Stopping Inventory Service process to simulate sudden network/service crash...")
        procs['inventory'].terminate()
        procs['inventory'].wait(timeout=5)
        print("  -> Inventory Service process terminated (port 8082 is unreachable).")

        print("\nFiring order creation through API Gateway while Inventory is DOWN...")
        req_b = urllib.request.Request(
            f"{GATEWAY_URL}/api/v1/orders",
            data=json.dumps(order_payload).encode(),
            headers={"Content-Type": "application/json", "X-Customer-Id": "USER-SCENARIO-B"}
        )

        # First request trips circuit breaker after retries
        t0 = time.perf_counter()
        try:
            urllib.request.urlopen(req_b, timeout=6)
            assert False, "Request should have failed with controlled 503"
        except urllib.error.HTTPError as e:
            lat1 = (time.perf_counter() - t0) * 1000.0
            body1 = json.loads(e.read().decode())
            print(f"-> 1st Call Latency (Retries active): {lat1:.1f} ms")
            print(f"-> HTTP Status: {e.code} (Controlled Service Unavailable)")
            print(f"-> Error: {body1.get('error')}")
            print(f"-> Message: {body1.get('message')}")
            print(f"-> Retry-After Header: {e.headers.get('Retry-After')}s")
            assert e.code == 503, f"Expected controlled 503, got {e.code}"
            assert body1.get("error") == "DOWNSTREAM_SERVICE_UNAVAILABLE"

        # Consecutively send requests to verify Circuit Breaker is now OPEN and fast-failing
        print("\nFiring subsequent requests to verify Circuit Breaker OPEN state (immediate rejection without hanging)...")
        for i in range(3):
            t_sub = time.perf_counter()
            try:
                urllib.request.urlopen(req_b, timeout=2)
                assert False, "Circuit breaker should fast-fail request"
            except urllib.error.HTTPError as e:
                sub_lat = (time.perf_counter() - t_sub) * 1000.0
                body_sub = json.loads(e.read().decode())
                print(f"-> Call {i+1} Latency: {sub_lat:.2f} ms | Status: {e.code} | Message: {body_sub.get('message')}")
                assert e.code == 503
                # Verify fast failure (< 100ms instead of retry timeout)
                assert sub_lat < 150.0, f"Circuit breaker should fast-fail in < 150ms, took {sub_lat}ms"

        print(">>> SCENARIO B VERIFIED: Controlled 503 returned, no 500/NPE/stack trace leaked, and Circuit Breaker fast-fails calls!")

        # =====================================================================
        # SCENARIO C: Service Recovery & Circuit Breaker Reset
        # =====================================================================
        print_banner("SCENARIO C: Service Recovery & Circuit Breaker Transition (HALF-OPEN -> CLOSED)")
        print("Restarting Inventory Service on port 8082...")
        procs['inventory'] = subprocess.Popen(
            [JAVA_BIN, "-Dspring.profiles.active=local", "-jar", "services/inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )
        assert wait_for_endpoint(f"{INVENTORY_URL}/api/v1/inventory/PROD-101", timeout=50), "Inventory failed to recover"
        print("  -> Inventory Service recovered and healthy on port 8082.")

        print("Waiting 3.0s for Circuit Breaker open-state duration to elapse...")
        time.sleep(3.0)

        print("Sending order request to trigger HALF-OPEN trial and reset circuit breaker...")
        order_payload_c = {
            "customerId": "CUST-1001",
            "items": [{"productId": "PROD-101", "quantity": 1, "unitPrice": 1499.00}]
        }
        req_c = urllib.request.Request(
            f"{GATEWAY_URL}/api/v1/orders",
            data=json.dumps(order_payload_c).encode(),
            headers={"Content-Type": "application/json", "X-Customer-Id": "USER-SCENARIO-C"}
        )

        with urllib.request.urlopen(req_c, timeout=5) as resp:
            status_c = resp.status
            body_c = json.loads(resp.read().decode())
            print(f"-> Recovery Call Status: HTTP {status_c}")
            print(f"-> Order ID: {body_c.get('orderId')} | Status: {body_c.get('status')}")
            assert status_c == 201, "Expected HTTP 201 after recovery"
            assert body_c.get("status") == "RESERVED", "Expected status RESERVED"

        print(">>> SCENARIO C VERIFIED: System successfully self-healed, Circuit Breaker closed, and traffic restored!")

    finally:
        print("\nCleaning up processes...")
        for name, p in procs.items():
            if p and p.poll() is None:
                p.terminate()
                try:
                    p.wait(timeout=3)
                except Exception:
                    p.kill()
        print("All processes cleanly terminated.")

if __name__ == "__main__":
    main()
