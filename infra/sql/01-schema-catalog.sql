-- Base de datos de catalog-service (opcional: Hibernate ddl-auto=update tambien las crea solas).
CREATE DATABASE IF NOT EXISTS db_catalog;
USE db_catalog;

CREATE TABLE IF NOT EXISTS product (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(255),
  price DECIMAL(10,2) NOT NULL,
  stock INT NOT NULL
);

CREATE TABLE IF NOT EXISTS stock_alert (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL UNIQUE,
  product_id BIGINT NOT NULL,
  product_name VARCHAR(120) NOT NULL,
  stock INT NOT NULL,
  threshold INT NOT NULL,
  created_at DATETIME(6) NOT NULL
);
