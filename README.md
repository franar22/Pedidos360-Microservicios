# Pedidos360 – Microservicios (Evaluación 2/3 · Cloud Native I)

Monolito Spring Boot de la Prueba 1 separado en **3 microservicios** con mensajería asíncrona
(**RabbitMQ**, **SNS + SQS**, **Amazon MQ** opcional), manteniendo lo que ya usabas:
**Cognito, RDS MySQL, API Gateway, Amplify y Lambda**.

```
                         Amplify (React)  ──login──▶  Cognito (JWT)
                                │  Bearer JWT
                                ▼
                 API Gateway (HTTP API + authorizer JWT)
          ┌─────────────────────┼──────────────────────┐
   /api/catalog/**       /api/orders/**          /api/rabbit/**
          ▼                     ▼                      ▼
  catalog-service ◀──REST──  order-service      rabbit-admin-service
   (8081) · db_catalog       (8082) · db_orders     (8083) · solo ADMIN
          │  ▲                  │   │                    │ crea/borra colas,
 stock.bajo │ consume           │   │                    │ exchanges, bindings
          ▼  │                  ▼   ▼                    ▼
        ┌──────────── RabbitMQ (clúster 2 nodos: rabbit1 + rabbit2) ───────────┐
        │ pedidos360.direct (direct) ─ticket.generar─▶ tickets.q        ─▶ .dlq │
        │ pedidos360.topic  (topic)  ─pedido.#──────▶ notificaciones.q ─▶ .dlq │
        │                            ─inventario.#──▶ inventario.q      ─▶ .dlq │
        └────────────────────────────────────────────────────────────────────┘
                                │ order-service también publica en
                                ▼
                   SNS  pedidos360-pedido-eventos  (fan-out)
                    ├─▶ SQS pedidos360-auditoria (+DLQ) ─▶ order-service (@SqsListener)
                    └─▶ SQS pedidos360-email     (+DLQ) ─▶ Lambda lambda-email
```

## ¿Cómo se separó el monolito?

| Microservicio | Origen | Qué contiene |
|---|---|---|
| `catalog-service` (8081) | **Extraído** del monolito | Productos, stock, reserva/devolución atómica de stock, alertas de stock bajo |
| `order-service` (8082) | **Extraído** del monolito | Pedidos, máquina de estados, ticket asíncrono, bitácora; llama al catálogo por REST |
| `rabbit-admin-service` (8083) | **Creado desde cero** | API REST para crear/eliminar colas, exchanges y bindings (rúbrica Evaluación 3) |
| `lambda-email/` | Guía del profesor (cap. 7) | Lambda consumidora de SQS con *batch item failures* |
| `frontend/` | Tu React/Vite | **Sin cambios de código**: las rutas `/api/catalog/**` y `/api/orders/**` son las mismas |

Decisiones clave (para defender en la presentación):
* **Base de datos por servicio** (`db_catalog`, `db_orders`) en el mismo RDS. No hay FK entre servicios.
* **Aceptar un pedido es síncrono hacia el catálogo** (`POST /api/catalog/stock/reserve`) porque la regla
  "no se acepta sin stock" necesita respuesta inmediata (la guía: *no usar colas cuando el usuario necesita la respuesta ahora*).
  Si luego falla guardar el estado, `order-service` compensa devolviendo el stock.
* **Lo no crítico es asíncrono**: notificaciones, tickets, alertas de stock, auditoría y email.
* **La mensajería nunca rompe el negocio**: los eventos se publican *después del commit*; si el broker está caído
  el pedido igual se guarda y el error queda en logs.
* Cada servicio **valida el JWT de Cognito** por su cuenta (además del authorizer de API Gateway).
* Secretos fuera del código: contraseña de RDS, etc. viajan por variables de entorno (`.env`).

## Estructura

```
Pedidos360-Microservicios/
├─ catalog-service/  order-service/  rabbit-admin-service/   (Maven, Spring Boot 3.5.7, Java 21, Dockerfile c/u)
├─ lambda-email/                  Lambda Java 21 (SQS → email)
├─ frontend/                      React + Vite (Amplify)
├─ infra/
│  ├─ rabbitmq/cluster.conf       formación automática del clúster
│  ├─ aws/00..05-*.sh             EC2, SNS/SQS/S3, Lambda, API Gateway, alarmas, security group
│  └─ sql/                        esquemas y migración desde el monolito
├─ docs/                          DESPLIEGUE-AWS.md · MAPEO-RUBRICAS.md · pruebas.http
├─ docker-compose.yml             clúster RabbitMQ + 3 microservicios (+ MySQL local opcional)
└─ .env.example
```

## Inicio rápido (local, sin AWS)

```bash
cp .env.example .env            # edita DB_PASSWORD (o usa MySQL local, ver abajo)
docker compose up -d --build
# Consolas RabbitMQ:  http://localhost:15672 (nodo 1)   http://localhost:15673 (nodo 2)
# Swagger:            http://localhost:8081/doc/swagger-ui.html  (8082, 8083)
```
MySQL local en vez de RDS: en `.env` pon `DB_HOST=mysql`, `DB_USER=root`, `DB_PASSWORD=<la que quieras>` y levanta con
`docker compose --profile local-db up -d --build`.

Compilar/probar sin Docker: `cd catalog-service && mvn clean package` (igual en los otros). Si un test te estorba: `-DskipTests`.

## Despliegue en AWS Learner Lab
Paso a paso en **[docs/DESPLIEGUE-AWS.md](docs/DESPLIEGUE-AWS.md)**. Resumen:
1. EC2 (Amazon Linux 2023, t3.medium+) → `00-ec2-bootstrap.sh` → subir proyecto → `docker compose up -d --build`
2. `01-create-sns-sqs.sh` → pegar variables en `.env`, `AWS_ENABLED=true`
3. `02-create-lambda-email.sh` (Lambda + trigger SQS)
4. `03-create-api-gateway.sh` (API Manager + JWT Cognito + rutas) → poner la URL en el frontend (Amplify)
5. `04-create-alarms.sh` (CloudWatch sobre las DLQ)

## Mapeo a las rúbricas
Ver **[docs/MAPEO-RUBRICAS.md](docs/MAPEO-RUBRICAS.md)** (cada indicador → archivo que lo cumple + cómo demostrarlo).
