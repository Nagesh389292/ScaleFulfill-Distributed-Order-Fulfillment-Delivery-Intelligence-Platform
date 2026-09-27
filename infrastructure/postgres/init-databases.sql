-- infrastructure/postgres/init-databases.sql
-- Create isolated databases for microservices in Phase 2
CREATE DATABASE order_db;
CREATE DATABASE inventory_db;

GRANT ALL PRIVILEGES ON DATABASE order_db TO scalefulfill_user;
GRANT ALL PRIVILEGES ON DATABASE inventory_db TO scalefulfill_user;
