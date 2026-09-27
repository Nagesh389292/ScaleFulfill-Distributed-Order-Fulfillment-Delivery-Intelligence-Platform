import requests

products = [
    {
        "product_id": "PROD-100",
        "sku": "SKU-LAPTOP-M3",
        "name": "Apple MacBook Pro M3 16-inch",
        "price": 2499.00,
        "weight_kg": 2.1,
        "available_quantity": 15,
        "fulfillment_centers": ["FC-BLR-01", "FC-HYD-01"],
        "search_text": "Apple MacBook Pro M3 16-inch Space Black laptop portable high performance"
    },
    {
        "product_id": "PROD-101",
        "sku": "SKU-MONITOR-4K",
        "name": "Dell UltraSharp 27 4K USB-C Hub Monitor",
        "price": 599.99,
        "weight_kg": 5.5,
        "available_quantity": 40,
        "fulfillment_centers": ["FC-BLR-01"],
        "search_text": "Dell UltraSharp 27 4K USB-C Hub Monitor IPS HDR display screen resolution"
    },
    {
        "product_id": "PROD-102",
        "sku": "SKU-KB-MECH",
        "name": "Logitech MX Mechanical Wireless Keyboard",
        "price": 169.99,
        "weight_kg": 0.8,
        "available_quantity": 0,
        "fulfillment_centers": ["FC-HYD-01"],
        "search_text": "Logitech MX Mechanical Wireless Keyboard tactile quiet office switch low profile"
    },
    {
        "product_id": "PROD-103",
        "sku": "SKU-MOUSE-MX3S",
        "name": "Logitech MX Master 3S Wireless Performance Mouse",
        "price": 99.99,
        "weight_kg": 0.14,
        "available_quantity": 25,
        "fulfillment_centers": ["FC-BLR-01", "FC-HYD-01"],
        "search_text": "Logitech MX Master 3S Wireless Performance Mouse quiet clicks 8K DPI ergonomic"
    },
    {
        "product_id": "PROD-104",
        "sku": "SKU-HEADSET-ANC",
        "name": "Sony WH-1000XM5 Wireless Noise Canceling Headphones",
        "price": 398.00,
        "weight_kg": 0.25,
        "available_quantity": 12,
        "fulfillment_centers": ["FC-HYD-01"],
        "search_text": "Sony WH-1000XM5 Wireless Noise Canceling Headphones silver audio lossless"
    }
]

for p in products:
    res = requests.post("http://localhost:8080/api/v1/search/products", json=p)
    print(f"Indexed {p['sku']}: HTTP {res.status_code}")
