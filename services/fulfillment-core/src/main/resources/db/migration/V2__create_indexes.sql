-- V2__create_indexes.sql
-- ScaleFulfill Performance & Lookup Indexes

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);

CREATE INDEX idx_inventory_product_id ON inventory(product_id);
CREATE INDEX idx_inventory_fc_id ON inventory(fulfillment_center_id);

CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_fulfillment_centers_code ON fulfillment_centers(code);
