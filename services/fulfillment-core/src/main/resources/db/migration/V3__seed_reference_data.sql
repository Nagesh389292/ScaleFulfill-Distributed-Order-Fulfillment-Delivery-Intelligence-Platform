-- V3__seed_reference_data.sql
-- Seed Fulfillment Centers, Products, Customers, and Initial Inventory

INSERT INTO customers (id, name, email) VALUES
('CUST-1001', 'Arjun Sharma', 'arjun.sharma@example.com'),
('CUST-1002', 'Priya Patel', 'priya.patel@example.com'),
('CUST-1003', 'Rohan Verma', 'rohan.verma@example.com');

INSERT INTO fulfillment_centers (id, code, name, latitude, longitude, active) VALUES
('FC-BLR-01', 'BLR-01', 'Bangalore Fulfillment Hub', 12.9716, 77.5946, TRUE),
('FC-HYD-01', 'HYD-01', 'Hyderabad Regional Center', 17.3850, 78.4867, TRUE),
('FC-MAA-01', 'MAA-01', 'Chennai Coastal Dispatch', 13.0827, 80.2707, TRUE),
('FC-DEL-01', 'DEL-01', 'Delhi NCR Mega Center', 28.7041, 77.1025, TRUE);

INSERT INTO products (id, sku, name, price, weight_kg) VALUES
('PROD-100', 'SKU-LAPTOP-X1', 'UltraBook Pro 15 inch', 64999.00, 1.850),
('PROD-101', 'SKU-MOUSE-WL', 'Wireless Ergonomic Mouse', 1499.00, 0.120),
('PROD-102', 'SKU-KBD-MECH', 'Mechanical Gaming Keyboard', 3999.00, 0.950),
('PROD-103', 'SKU-HDPH-NC', 'Noise-Cancelling Headphones', 7999.00, 0.280);

-- Seed Distributed Inventory across Fulfillment Centers
INSERT INTO inventory (product_id, fulfillment_center_id, available_quantity, reserved_quantity, version) VALUES
('PROD-100', 'FC-BLR-01', 50, 0, 0),
('PROD-100', 'FC-HYD-01', 30, 0, 0),
('PROD-100', 'FC-MAA-01', 20, 0, 0),
('PROD-101', 'FC-BLR-01', 200, 0, 0),
('PROD-101', 'FC-HYD-01', 150, 0, 0),
('PROD-102', 'FC-BLR-01', 80, 0, 0),
('PROD-103', 'FC-DEL-01', 100, 0, 0);
