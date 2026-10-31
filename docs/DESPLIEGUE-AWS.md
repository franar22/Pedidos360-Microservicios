# Despliegue en AWS Academy Learner Lab

Reutiliza lo de la Prueba 1: **User Pool de Cognito**, **RDS MySQL** y **Amplify**. Región `us-east-1`.
Credenciales del lab: caducan cada ~4 h (ver "Troubleshooting").

## 0. Subir el proyecto a GitHub
`.gitignore` ya excluye `.env`, `target/` y `node_modules/`. Sugerido: un repo para `frontend/` y otro para el backend (el enunciado pide enlaces de GitHub).

## 1. EC2 con docker-compose (aquí corren RabbitMQ y los 3 microservicios)
1. EC2 → *Launch instance*: Amazon Linux 2023, **t3.medium** (4 GB; con t3.small no alcanza para 3 JVM + 2 RabbitMQ), key pair, IAM instance profile **LabInstanceProfile**.
2. Security Group (o `infra/aws/05-security-group-ports.sh`): 22 y 15672/15673 desde **tu IP**; 8081-8083 abiertos (API Gateway no tiene IP fija).
3. RDS (Prueba 1): en su Security Group permitir **3306** desde el SG de la EC2.
4. Contenedores y credenciales: `aws ec2 modify-instance-metadata-options --instance-id <id> --http-put-response-hop-limit 2 --http-tokens required`
   (así los contenedores obtienen credenciales del rol de la instancia; alternativa: `docker-compose.aws-creds.yml`).
5. En la EC2: `bash infra/aws/00-ec2-bootstrap.sh`, cerrar/abrir sesión, clonar/subir el proyecto.
6. `cp .env.example .env` y editar `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `RABBITMQ_*`.
7. `docker compose up -d --build` (la primera vez tarda ~5-8 min compilando).
8. Verificar: `docker compose ps` (todo *healthy*), `curl localhost:8081/api/catalog/health`, consola RabbitMQ `http://<ip-ec2>:15672`
   → pestaña *Admin → Nodes*: **2 nodos** `rabbit@rabbit1` y `rabbit@rabbit2`.

## 2. SNS + SQS + S3
Desde CloudShell o la EC2: `bash infra/aws/01-create-sns-sqs.sh`. Copia las variables que imprime al `.env` (`AWS_ENABLED=true`, `SNS_TOPIC_ARN`, `TICKETS_BUCKET`)
y ejecuta `docker compose up -d order-service`.

## 3. Lambda de email (consumidor SQS)
Desde la raíz del proyecto (Maven + Java 21): `bash infra/aws/02-create-lambda-email.sh`. Usa `LabRole`, Java 21, 512 MB, 10 s, batch 10, *Report batch item failures*.

## 4. API Gateway (API Manager) con JWT
```bash
EC2_HOST=ec2-xx-xx-xx-xx.compute-1.amazonaws.com \
FRONTEND_ORIGIN=https://360pedidos.d3okqezea05os0.amplifyapp.com \
bash infra/aws/03-create-api-gateway.sh
```
Crea el HTTP API `pedidos360-api`, el *authorizer* JWT de Cognito, las rutas `/api/catalog|orders|rabbit` y CORS.
(Si ya tenías el API de la Prueba 1 puedes borrarlo o reutilizar su dominio.)

## 5. Frontend (Amplify)
En Amplify → *Environment variables*: `VITE_API_URL` = URL del API nuevo, `VITE_COGNITO_AUTHORITY`, `VITE_COGNITO_CLIENT_ID`. Re-desplegar.
Recuerda que los usuarios de Cognito (admin / operador / cliente) y sus grupos son los de la Prueba 1.

## 6. Alarmas CloudWatch
`bash infra/aws/04-create-alarms.sh` (se disparan si una DLQ de SQS tiene mensajes).

## 7. Migrar datos del monolito (opcional)
Levanta los servicios una vez (crean tablas) y ejecuta `infra/sql/03-migrate-from-monolith.sql` contra el RDS.

---

## Opción B: Amazon MQ for RabbitMQ en vez de docker-compose
1. Amazon MQ → *Create broker* → **RabbitMQ**, *single-instance*, `mq.t3.micro`, usuario/clave. (Si el lab lo permite, *cluster deployment* da 3 nodos.)
2. En `.env` agrega y pasa a los servicios: `RABBITMQ_ADDRESSES=b-xxxx.mq.us-east-1.amazonaws.com:5671`, `RABBITMQ_SSL=true`,
   `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, y para el admin `RABBITMQ_MANAGEMENT_URL=https://b-xxxx.mq.us-east-1.on.aws`.
3. En `docker-compose.yml` quita (o no levantes) `rabbit1/rabbit2/rabbit-setup` y las líneas fijas `RABBITMQ_ADDRESSES` de `x-java-service`.
4. El *security group* del broker debe permitir 5671 desde la EC2.
> Para la presentación de la Evaluación 2 (dos nodos en clúster + docker-compose) usa la opción docker-compose.

## Opción C: microservicios como Lambda (sin colas)
Cada API puede seguir como Lambda detrás de API Gateway, igual que en la Prueba 1:
`cd catalog-service && mvn clean package -Plambda` → subir `target/catalog-service-1.0.0.jar`
(handler `com.pedidos360.catalog.StreamLambdaHandler::handleRequest`; `order-service` análogo con `com.pedidos360.order...`).
Variables: las de `.env` + `PEDIDOS360_MESSAGING_ENABLED=false` (las Lambdas no sostienen consumidores) y en order-service `CATALOG_SERVICE_URL=<url del API Gateway>`.
Con esta opción **no hay colas**: úsala solo como respaldo.

## Troubleshooting
| Síntoma | Causa / solución |
|---|---|
| `ExpiredToken...` en SQS/SNS/S3 | Credenciales del lab caducaron. Re-lanzar el lab y `docker compose restart order-service` (o credenciales nuevas en `docker-compose.aws-creds.yml`). |
| Contenedor `order-service` no llega a SNS (401 / "Unable to load credentials") | Hop limit de metadatos = 1. Paso 1.4. |
| `Connection timed out` a RDS | SG de RDS sin regla 3306 desde la EC2. |
| `QueueDoesNotExist` | Región distinta o `01-create-sns-sqs.sh` no se ejecutó. Mantener `AWS_ENABLED=false` hasta crearlas. |
| `PRECONDITION_FAILED` al arrancar | Cola ya creada con otros argumentos (p. ej. desde el admin). Borrarla y reiniciar el servicio. |
| 401 desde API Gateway | Falta el token o `Audience`/`Issuer` del authorizer no coinciden con tu App Client. El frontend envía el *id_token*. |
| 403 en `/api/rabbit/**` | Solo el grupo **ADMIN** de Cognito. |
| 503 "El catálogo no está disponible" | `catalog-service` caído o `CATALOG_SERVICE_URL` incorrecta. |
| Mensajes "desaparecen" | Revisar `*.dlq` en la consola de RabbitMQ / SQS. |

## Limpieza (costos)
Detén la instancia RDS y la EC2 al terminar la clase; elimina Lambda, trigger, colas, tópico, bucket y (si lo usaste) el broker de Amazon MQ.
