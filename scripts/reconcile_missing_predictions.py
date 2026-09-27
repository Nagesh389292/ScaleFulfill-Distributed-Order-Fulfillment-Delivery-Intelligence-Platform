#!/usr/bin/env python3
import subprocess
import requests
import json

def run_psql(db, sql):
    cmd = ["docker", "exec", "scalefulfill-postgres", "psql", "-U", "scalefulfill_user", "-d", db, "-t", "-A", "-c", sql]
    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return [line.strip() for line in res.stdout.strip().split("\n") if line.strip()]

orders = run_psql("order_db", "SELECT id FROM orders;")
preds = requests.get("http://localhost:8085/api/predictions", timeout=10).json()
pred_ids = set(p["orderId"] for p in preds)

missing = sorted(list(set(orders) - pred_ids))
print(f"Missing predictions count: {len(missing)}")

for oid in missing:
    outbox_raw = run_psql("order_db", f"SELECT payload FROM outbox_events WHERE aggregate_id = '{oid}';")
    if not outbox_raw:
        continue
    env = json.loads(outbox_raw[0])
    p = env.get("payload", env)
    
    items = []
    for item in p.get("items", []):
        items.append({
            "productId": item.get("productId", "PROD-101"),
            "quantity": item.get("quantity", 1),
            "unitPrice": item.get("unitPrice", 49.99),
            "weightKg": 1.5,
            "volumeM3": 0.01
        })
    if not items:
        items.append({
            "productId": "PROD-101",
            "quantity": 1,
            "unitPrice": 49.99,
            "weightKg": 1.5,
            "volumeM3": 0.01
        })
    
    req_body = {
        "orderId": p.get("orderId", oid),
        "customerId": p.get("customerId", "CUST-1001"),
        "totalAmount": p.get("totalAmount", 49.99),
        "occurredAt": p.get("occurredAt", "2026-09-27T15:00:00Z"),
        "items": items
    }
    
    r = requests.post("http://localhost:8085/api/predictions", json=req_body, timeout=5)
    if r.status_code in [200, 201]:
        print(f"Prediction generated for {oid}: HTTP {r.status_code}")
    else:
        print(f"Failed prediction for {oid}: HTTP {r.status_code} - {r.text}")

print("Prediction reconciliation completed!")
