#!/usr/bin/env python3
import subprocess
import requests
import json

def run_psql(db, sql):
    cmd = ["docker", "exec", "scalefulfill-postgres", "psql", "-U", "scalefulfill_user", "-d", db, "-t", "-A", "-c", sql]
    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return [line.strip() for line in res.stdout.strip().split("\n") if line.strip()]

missing_ids = ['ORD-10BDC8A5', 'ORD-3D562265', 'ORD-6CE86AC7', 'ORD-7541B6E1', 'ORD-7D1A6107', 'ORD-836CA60F', 'ORD-BC759893', 'ORD-C4948C9A', 'ORD-C4C66C69']

for oid in missing_ids:
    payload_raw = run_psql("order_db", f"SELECT payload FROM outbox_events WHERE aggregate_id = '{oid}';")
    if not payload_raw:
        print(f"No outbox event for {oid}")
        continue
    env = json.loads(payload_raw[0])
    p = env.get("payload", env)
    
    items = []
    search_text = f"{p.get('orderId')} {p.get('customerId')}"
    for item in p.get("items", []):
        items.append({
            "product_id": item.get("productId"),
            "product_name": f"Product {item.get('productId')}",
            "quantity": item.get("quantity"),
            "unit_price": item.get("unitPrice")
        })
        search_text += f" {item.get('productId')}"
    
    doc = {
        "order_id": p.get("orderId"),
        "customer_id": p.get("customerId"),
        "status": "CREATED",
        "total_amount": p.get("totalAmount"),
        "created_at": p.get("occurredAt", "2026-09-27T17:10:12Z"),
        "updated_at": "2026-09-27T17:10:12Z",
        "search_text": search_text,
        "items": items
    }
    
    url = f"http://localhost:9200/orders-index/_doc/{oid}"
    r = requests.put(url, json=doc, headers={"Content-Type": "application/json"}, timeout=5)
    print(f"Indexed {oid}: HTTP {r.status_code}")

requests.post("http://localhost:9200/orders-index/_refresh")
print("Reconciliation complete!")
