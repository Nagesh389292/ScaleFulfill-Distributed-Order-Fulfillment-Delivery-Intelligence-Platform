#!/usr/bin/env python3
import subprocess
import requests
import json

def run_psql(db, sql):
    cmd = ["docker", "exec", "scalefulfill-postgres", "psql", "-U", "scalefulfill_user", "-d", db, "-t", "-A", "-c", sql]
    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return [line.strip() for line in res.stdout.strip().split("\n") if line.strip()]

# 1. Orders from PostgreSQL
orders_raw = run_psql("order_db", "SELECT id FROM orders;")
orders = set(orders_raw)

# 2. Outbox events from PostgreSQL
outbox_lines = run_psql("order_db", "SELECT id, aggregate_id, status FROM outbox_events;")
outbox_map = {}
outbox_published = set()
outbox_pending = set()
for line in outbox_lines:
    parts = line.split("|")
    if len(parts) >= 3:
        eid, oid, status = parts[0], parts[1], parts[2]
        outbox_map[eid] = {"orderId": oid, "status": status}
        if status == "PUBLISHED":
            outbox_published.add(oid)
        elif status == "PENDING":
            outbox_pending.add(oid)

# 3. Inventory processed events
inv_lines = run_psql("inventory_db", "SELECT event_id FROM processed_events WHERE consumer_group = 'inventory-service-group';")
inv_processed_events = set(inv_lines)

# 4. OpenSearch documents
r = requests.post("http://localhost:9200/orders-index/_search", json={
    "size": 10000,
    "_source": ["order_id"]
}, timeout=10)
os_hits = r.json().get("hits", {}).get("hits", [])
os_order_ids = set(hit["_source"]["order_id"] for hit in os_hits if "order_id" in hit.get("_source", {}))

# Audit Metrics
total_orders = len(orders)
missing_outbox = orders - outbox_published
pending_outbox_count = len(outbox_pending)

# For every order, verify it has an event that was processed in inventory
orders_with_inventory = set()
for eid, data in outbox_map.items():
    if eid in inv_processed_events:
        orders_with_inventory.add(data["orderId"])

missing_inventory = orders - orders_with_inventory
missing_search = orders - os_order_ids

print("===========================================================================")
print("SCALEFULFILL CORRELATION-BASED CONSISTENCY AUDIT")
print("===========================================================================")
print(f"Total Persistent Orders (Postgres)     : {total_orders}")
print(f"Published Outbox Events               : {len(outbox_published)}")
print(f"Pending Outbox Events                 : {pending_outbox_count}")
print(f"Inventory Processed Events Total      : {len(inv_processed_events)}")
print(f"OpenSearch Unique Orders Indexed      : {len(os_order_ids)}")
print("---------------------------------------------------------------------------")
print(f"Orphan Orders (no published outbox)   : {len(missing_outbox)}")
print(f"Pending Outbox Events                 : {pending_outbox_count}")
print(f"Missing Inventory Processing Records  : {len(missing_inventory)}")
print(f"Missing OpenSearch Projections        : {len(missing_search)}")
if missing_search:
    print(f"Sample missing orders in OpenSearch: {list(missing_search)[:5]}")
    for oid in list(missing_search)[:5]:
        outbox_info = run_psql("order_db", f"SELECT id, status, created_at FROM outbox_events WHERE aggregate_id = '{oid}';")
        print(f"  {oid} -> Outbox: {outbox_info}")
print("===========================================================================")
if len(missing_outbox) == 0 and pending_outbox_count == 0 and len(missing_inventory) == 0 and len(missing_search) == 0:
    print("[VERDICT] Master Invariant Holds True: Complete End-to-End Correlation (0 Drift, 0 Loss)!")
else:
    print(f"[VERDICT] Invariant Incomplete: missing_inv={len(missing_inventory)}, missing_search={len(missing_search)}")
