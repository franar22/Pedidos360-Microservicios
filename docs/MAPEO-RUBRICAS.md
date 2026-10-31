# Mapeo a las rúbricas (Evaluación Parcial 2 y 3 – RabbitMQ) y a la guía del profesor

## Evaluación 3 – Encargo (código)

| Indicador (peso) | Dónde está | Cómo demostrarlo |
|---|---|---|
| Nombres de colas/exchanges/bindings centralizados (12%) | `application.yml` → `pedidos360.rabbit.*` + `config/RabbitNamesProperties.java` (en los 3 servicios, idénticos). Los consumidores usan `${pedidos360.rabbit.queues.*}`. | Buscar nombres de colas (`.q`, `pedidos360.direct`) en `*.java`: solo aparecen prefijos de propiedades, ningún nombre de cola. |
| Beans `Queue`/`Exchange`/`Binding` por caso de uso (13%) | `config/RabbitTopologyConfig.java`: notificaciones (topic), tickets (direct), inventario (topic) + DLX + una DLQ por cola. | Consola RabbitMQ → Exchanges/Queues. |
| Negocio separado de la configuración de mensajería (10%) | `OrderService`/`StockService` solo publican un evento de dominio (`ApplicationEventPublisher`); `messaging/publisher/*` y `config/Rabbit*` aíslan RabbitMQ. | Mostrar que `OrderService` no importa nada de AMQP. |
| Consumidores con `@RabbitListener` por dominio (15%) | `order-service/.../messaging/consumer/notificacion`, `.../ticket`; `catalog-service/.../consumer/inventario`; SQS: `.../consumer/auditoria` (`@SqsListener`). | Paquetes por dominio. |
| ACK y manejo de errores (20%) | `messaging/support/ReliableMessageProcessor.java` + `ack-mode: manual`, `default-requeue-rejected: false`: ACK explícito; no recuperable → NACK sin requeue → DLQ; recuperable → 3 reintentos con backoff → DLQ; todo con log `MENSAJE A DLQ`. Consumidores idempotentes. | `docs/pruebas.http` #14 (JSON corrupto) y #15 (3 reintentos y DLQ); ver logs y la DLQ. |
| API REST del administrador (13%) | `rabbit-admin-service/controller/*`: `POST/GET/DELETE /api/rabbit/queues`, `/exchanges`, `/bindings`, `DELETE /queues/{name}/messages`, `POST /messages`. | Swagger `:8083/doc/swagger-ui.html`. |
| Servicio dedicado `RabbitAdminService` (10%) | `rabbit-admin-service/service/RabbitAdminService.java` (único que toca `RabbitAdmin`). | Controladores sin imports de AMQP. |
| Validación de entrada (7%) | DTOs `QueueRequest`, `ExchangeRequest`, `BindingRequest`, `PublishRequest` con Bean Validation + `GlobalExceptionHandler` (400 con mensajes claros, 404, 409). Test: `RequestValidationTest`. | `pruebas.http` #9 (nombre vacío → 400). |

Además (enunciado Ev.3): filtros JWT de Cognito en cada servicio (`SecurityConfig` + `CognitoGroupsConverter`), integración con RDS (entidades/repositorios/propiedades por variables de entorno), mensajes no entregados a DLQ y registrados en logs, `.gitignore` por tecnología, pruebas básicas (`mvn test`).

## Evaluación 2 – Presentación (checklist de demostración)

| Debes mostrar | Cómo |
|---|---|
| API Manager creado y configurado hacia el backend | API Gateway → `pedidos360-api` → Routes (`/api/catalog|orders|rabbit`) → Integrations (EC2). Script `03-create-api-gateway.sh`. |
| Frontend consume vía API Manager | Amplify con `VITE_API_URL` = URL del API Gateway; DevTools → Network. |
| API Manager valida JWT (rechaza/acepta) | `curl` sin token → 401; con token → 200 (`pruebas.http` #1 y #2). Authorizer `cognito-jwt`. |
| Tenant/IDaaS y usuarios | Cognito User Pool (Prueba 1): usuarios admin/operador/cliente y grupos. |
| Frontend usa OAuth2/OIDC y obtiene JWT | Login de Cognito (react-oidc-context) → token en `sessionStorage`. |
| Backend y frontend desplegados e integrados | EC2 (`docker compose ps`) + Amplify. |
| **Dos nodos RabbitMQ en clúster** | Consola `:15672` → Admin → Nodes (rabbit1 y rabbit2) o `docker exec rabbit1 rabbitmqctl cluster_status`. Config: `infra/rabbitmq/cluster.conf`. |
| **Tres colas con sus DLQ funcionando** | Queues: `notificaciones.q`, `tickets.q`, `inventario.q` y sus `.dlq`; provocar DLQ con `pruebas.http` #14/#15. |
| **Exchanges direct y topic** | Exchanges: `pedidos360.direct` (direct) y `pedidos360.topic` (topic) + Bindings. |
| **docker-compose levantando RabbitMQ en la nube** | `docker compose ps` en la EC2 con `rabbit1`, `rabbit2`. |

## Guía del profesor (SQS/SNS/Lambda/CloudWatch)
Fan-out SNS → 2 colas SQS con DLQ y `maxReceiveCount=3` (`01-create-sns-sqs.sh`), long polling 20 s, visibility timeout 60 s,
consumidor `@SqsListener` idempotente (`event_id` UNIQUE), Lambda con `SQSBatchResponse`, tickets en S3 (claim check),
alarma de DLQ en CloudWatch (`04-create-alarms.sh`), logs con el id del pedido, y patrón Outbox documentado como mejora (dual-write).

## Limitaciones conocidas (para ser honesto en la defensa)
* Publicación post-commit sin *Transactional Outbox*: si el proceso muere entre el commit y el envío, se pierde el evento.
* Reserva de stock síncrona + compensación (mini-saga) en lugar de una saga completa.
* El correo/notificación del laboratorio se simula con logs (SES requiere identidades verificadas).
* Colas clásicas con política HA `ha-mode: all` sobre 2 nodos (en producción: *quorum queues* con 3 nodos).
