import requests
import json

BASE_URL = "http://localhost:8080/api/v1/search"

def test_query(endpoint, params, desc):
    url = f"{BASE_URL}/{endpoint}"
    r = requests.get(url, params=params)
    data = r.json()
    print(f"=== {desc} ===")
    print(f"URL: {r.url}")
    print(f"Status: {r.status_code}")
    print(f"Execution Time: {data.get('executionTimeMs')} ms | Total Hits: {data.get('totalHits')}")
    results = data.get("results", [])
    for idx, item in enumerate(results[:5]):
        if endpoint == "products":
            print(f"  {idx+1}. [{item.get('sku')}] {item.get('name')} | ${item.get('price')} | Stock: {item.get('available_quantity')} | FCs: {item.get('fulfillment_centers')}")
        else:
            print(f"  {idx+1}. [{item.get('order_id')}] Customer: {item.get('customer_id')} | Status: {item.get('status')} | Total: ${item.get('total_amount')} | Date: {item.get('created_at')}")
    if data.get("statusAggregation"):
        print(f"Status Facet Aggregation: {data.get('statusAggregation')}")
    if data.get("totalAmountSum") is not None:
        print(f"Total Revenue Sum Aggregation: ${data.get('totalAmountSum'):,.2f}")
    print()

if __name__ == "__main__":
    print("---------------- PRODUCT SEARCH ----------------")
    test_query("products", {"query": "headphone"}, "Fuzzy Full-Text: 'headphone'")
    test_query("products", {"inStockOnly": "true"}, "Filter: In-stock only (exclude out-of-stock keyboard)")
    test_query("products", {"minPrice": "200", "maxPrice": "1000"}, "Filter: Price Range $200 - $1,000")
    test_query("products", {"fcId": "FC-BLR-01"}, "Filter: Fulfillment Center FC-BLR-01")

    print("---------------- ORDER SEARCH ----------------")
    test_query("orders", {"customerId": "CUST-1001", "size": "3"}, "Filter: Customer ID CUST-1001")
    test_query("orders", {"minAmount": "100000", "size": "3"}, "Filter: Orders >= $100,000")
    test_query("orders", {"query": "PROD-102", "size": "3"}, "Search across nested items: 'PROD-102'")
    test_query("orders", {"size": "5"}, "All Orders with Facet Aggregations")
