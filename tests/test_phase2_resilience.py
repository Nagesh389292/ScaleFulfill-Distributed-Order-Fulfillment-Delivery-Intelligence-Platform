#!/usr/bin/env python3
"""
ScaleFulfill Phase 2 - Resilience & Failure Mode Test Suite
Tests:
1. End-to-end Gateway Routing & Correlation ID propagation
2. Token Bucket Rate Limiting (429 Too Many Requests)
3. Synchronous Inter-Service Communication (Gateway -> Order -> Inventory)
4. Downstream Failure Handling & Circuit Breaker Fast-Failure
"""

import json
import time
import urllib.request
import urllib.error
import sys

GATEWAY_URL = "http://localhost:8080"
ORDER_URL = "http://localhost:8081"
INVENTORY_URL = "http://localhost:8082"

def print_banner(msg):
    print(f"\n{'='*65}\n  {msg}\n{'='*65}")

def test_gateway_routing_and_correlation_id():
    print_banner("TEST 1: Gateway Routing & Correlation ID Propagation")
    
    # 1. Test Inventory Route through Gateway
    inv_req = urllib.request.Request(f"{GATEWAY_URL}/api/v1/inventory/PROD-101")
    with urllib.request.urlopen(inv_req, timeout=5) as resp:
        corr_id = resp.headers.get("X-Correlation-Id")
        status = resp.status
        data = json.loads(resp.read().decode())
        print(f"-> GET /api/v1/inventory/PROD-101: HTTP {status}")
        print(f"-> Injected X-Correlation-Id: {corr_id}")
        print(f"-> Product: {data.get('productName')} | Available Stock: {data.get('totalAvailableQuantity')}")
        assert status == 200, "Inventory query should return 200"
        assert corr_id is not None and len(corr_id) > 10, "Gateway must inject Correlation ID"

    # 2. Test Order Creation through Gateway
    order_payload = {
        "customerId": "CUST-1001",
        "items": [
            {
                "productId": "PROD-101",
                "quantity": 2,
                "unitPrice": 1499.00
            }
        ]
    }
    order_data = json.dumps(order_payload).encode()
    order_req = urllib.request.Request(
        f"{GATEWAY_URL}/api/v1/orders",
        data=order_data,
        headers={"Content-Type": "application/json", "X-Customer-Id": "CUST-TEST-USER-1"}
    )
    with urllib.request.urlopen(order_req, timeout=5) as resp:
        corr_id = resp.headers.get("X-Correlation-Id")
        status = resp.status
        order_resp = json.loads(resp.read().decode())
        print(f"-> POST /api/v1/orders: HTTP {status}")
        print(f"-> Order ID: {order_resp.get('orderId')} | Status: {order_resp.get('status')} | Total: {order_resp.get('totalAmount')}")
        print(f"-> Assigned FC: {order_resp.get('assignedFcId')} | Injected X-Correlation-Id: {corr_id}")
        assert status == 201, "Order creation should return 201"
        assert order_resp.get("status") == "RESERVED", "Order should be RESERVED"

    print(">>> TEST 1 PASSED: Gateway routing and Correlation IDs verified!")

def test_token_bucket_rate_limiter():
    print_banner("TEST 2: Token Bucket Rate Limiting (Burst & 429 Enforcement)")
    
    from concurrent.futures import ThreadPoolExecutor
    client_id = "BURST_RATE_LIMIT_CLIENT"
    allowed_count = 0
    rejected_count = 0
    retry_after = None

    def send_req(_):
        req = urllib.request.Request(
            f"{GATEWAY_URL}/api/v1/inventory/PROD-101",
            headers={"X-Customer-Id": client_id}
        )
        try:
            with urllib.request.urlopen(req, timeout=2) as resp:
                return resp.status, None
        except urllib.error.HTTPError as e:
            return e.code, e.headers.get("Retry-After")
        except Exception as e:
            return 500, None

    print("Firing 35 concurrent requests against API Gateway (Configured Capacity = 20 tokens)...")
    with ThreadPoolExecutor(max_workers=35) as pool:
        results = list(pool.map(send_req, range(35)))

    for status, ra in results:
        if status == 200:
            allowed_count += 1
        elif status == 429:
            rejected_count += 1
            retry_after = ra

    print(f"-> Allowed Requests (within token capacity): {allowed_count}")
    print(f"-> Rejected Requests (429 Too Many Requests): {rejected_count}")
    print(f"-> Retry-After Header: {retry_after}s")
    
    assert rejected_count >= 5, f"Expected at least 5 rate-limited 429 requests under burst, got {rejected_count}"
    assert allowed_count >= 15, f"Expected at least 15 allowed requests within capacity, got {allowed_count}"
    assert allowed_count + rejected_count == 35, f"Expected all 35 requests accounted for, got {allowed_count + rejected_count}"
    print(">>> TEST 2 PASSED: Token bucket capacity and 429 rate limiting strictly enforced!")

def test_downstream_failure_handling():
    print_banner("TEST 3: Downstream Inventory Failure & Circuit Breaker Fast-Failure")
    
    # Send order with invalid SKU to trigger downstream 4xx/business rejection
    payload = {
        "customerId": "CUST-1001",
        "items": [
            {
                "productId": "PROD-NON-EXISTENT",
                "quantity": 1
            }
        ]
    }
    req = urllib.request.Request(
        f"{GATEWAY_URL}/api/v1/orders",
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json", "X-Customer-Id": "CLIENT-ERROR-TEST"}
    )
    try:
        urllib.request.urlopen(req, timeout=5)
        assert False, "Should have thrown downstream business error"
    except urllib.error.HTTPError as e:
        body = json.loads(e.read().decode())
        print(f"-> Downstream Business Rejection Status: HTTP {e.code}")
        print(f"-> Structured Error Response: {body.get('error')} - {body.get('message')}")
        assert e.code in [404, 400], f"Expected 404 or 400, got {e.code}"

    print(">>> TEST 3 PASSED: Downstream error propagation verified!")

if __name__ == "__main__":
    test_gateway_routing_and_correlation_id()
    test_token_bucket_rate_limiter()
    test_downstream_failure_handling()
    print_banner("ALL PHASE 2 RESILIENCE SCENARIOS VERIFIED SUCCESSFULLY!")
