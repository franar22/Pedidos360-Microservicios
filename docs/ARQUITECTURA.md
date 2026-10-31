# Arquitectura y flujos

## Flujo 1 – Crear pedido
1. `POST /api/orders` → API Gateway valida JWT → `order-service` valida JWT de nuevo.
2. `order-service` consulta cada producto a `catalog-service` (`GET /api/catalog/products/{id}`, reenviando el JWT).
3. Guarda el pedido (`CREADO`) y publica un `OrderEvent` en el commit.
4. Post-commit: RabbitMQ (topic `pedido.creado` → cola notificaciones) y SNS (→ auditoría + email).
5. La respuesta HTTP **no espera** a ningún consumidor.

## Flujo 2 – Aceptar pedido (`PUT /api/orders/{id}/status` = ACEPTADO)
1. Valida la transición con `OrderStateMachine`.
2. **Sincrónico**: `POST /api/catalog/stock/reserve` (atómico; 409 si no hay stock). Con bloqueo pesimista por fila.
3. Guarda el estado; si falla, compensa con `/stock/release`.
4. Post-commit: topic `pedido.estado.aceptado` (notificación), **direct `ticket.generar`** (ticket asíncrono) y SNS.
5. Si el stock queda ≤ umbral, `catalog-service` publica `inventario.stock.bajo` (topic) y su consumidor guarda la alerta.

## Contrato del evento (`OrderEvent`)
`eventId, type (PedidoCreado | PedidoEstadoCambiado), version, occurredAt, orderId, customerId, customerEmail, previousStatus, status, total, items[]`
(IDs y datos mínimos; mensaje persistente con `messageId = eventId`).

## Manejo de errores en consumidores RabbitMQ
```
procesa OK ........................ ACK
JSON/campos inválidos ............. NACK(requeue=false) → DLQ        (no recuperable)
fallo recuperable ................. reintento 1..3 con backoff → NACK(requeue=false) → DLQ
apagado del servicio .............. NACK(requeue=true)
```
Cada mensaje a DLQ deja un log `MENSAJE A DLQ queue=… dlq=… messageId=… motivo=… body=…`.

## Mejora de producción: Transactional Outbox
Guardar el evento en una tabla `outbox` dentro de la misma transacción y publicarlo con un `@Scheduled`. Elimina el *dual write*.
