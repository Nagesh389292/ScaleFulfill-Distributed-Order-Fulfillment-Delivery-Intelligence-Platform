-- V1__init_inventory_schema.sql
-- Inventory Service Dedicated Schema

CREATE TABLE products (
    id VARCHAR(64) PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    price NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    weight_kg NUMERIC(8, 3) NOT NULL DEFAULT 0.500,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE fulfillment_centers (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL REFERENCES products(id),
    fulfillment_center_id VARCHAR(64) NOT NULL REFERENCES fulfillment_centers(id),
    available_quantity INT NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_product_fc UNIQUE (product_id, fulfillment_center_id)
);

CREATE INDEX idx_inventory_product_id ON inventory(product_id);
CREATE INDEX idx_inventory_fc_id ON inventory(fulfillment_center_id);
CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_fulfillment_centers_code ON fulfillment_centers(code);
