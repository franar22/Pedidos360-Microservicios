-- Base de datos de order-service (opcional: Hibernate ddl-auto=update tambien las crea solas).
-- No hay FK hacia product: el catalogo vive en otra base de datos (database-per-service).
CREATE DATABASE IF NOT EXISTS db_orders;
USE db_orders;

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id VARCHAR(100) NOT NULL,
  customer_email VARCHAR(255),
  status VARCHAR(20) NOT NULL,
  total DECIMAL(12,2) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_name VARCHAR(120) NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS order_audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL UNIQUE,
  order_id BIGINT NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  previous_status VARCHAR(20),
  status VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS order_ticket (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL UNIQUE,
  content TEXT NOT NULL,
  s3_key VARCHAR(255),
  created_at DATETIME(6) NOT NULL
);
