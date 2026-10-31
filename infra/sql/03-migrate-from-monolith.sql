-- Copia los datos del monolito (db_pedidos360) a las bases de los microservicios.
-- Ejecutar UNA vez, despues de 01 y 02 (o de haber levantado los servicios), conectado al mismo RDS:
--   mysql -h <endpoint-rds> -u admin -p < infra/sql/03-migrate-from-monolith.sql
-- Conserva los ids, asi los pedidos antiguos siguen apuntando a los mismos productos.

INSERT INTO db_catalog.product (id, name, description, price, stock)
SELECT id, name, description, price, stock FROM db_pedidos360.product
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO db_orders.orders (id, customer_id, customer_email, status, total, created_at, updated_at)
SELECT id, customer_id, customer_email, status, total, created_at, updated_at FROM db_pedidos360.orders
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO db_orders.order_items (id, order_id, product_id, product_name, quantity, unit_price)
SELECT id, order_id, product_id, product_name, quantity, unit_price FROM db_pedidos360.order_items
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);
