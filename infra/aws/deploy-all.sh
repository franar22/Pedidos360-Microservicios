#!/usr/bin/env bash
# =====================================================================================
#  DESPLIEGUE AUTOMATICO DE PEDIDOS360 EN AWS (Learner Lab)
#  Ejecutar en AWS CloudShell (icono >_ de la consola) con el Learner Lab iniciado:
#
#     git clone https://github.com/franar22/Pedidos360-Microservicios.git
#     cd Pedidos360-Microservicios
#     bash infra/aws/deploy-all.sh
#
#  Hace, en orden (es re-ejecutable):
#   1. SNS + SQS (+DLQ) + bucket S3                          (01-create-sns-sqs.sh)
#   2. Security Group + permiso EC2 -> RDS (3306); enciende el RDS si esta detenido
#   3. EC2 t3.medium que se auto-configura (Docker, clona el repo, crea .env, docker compose up)
#   4. Espera a que los 3 microservicios respondan
#   5. Lambda de email                                        (02-create-lambda-email.sh)
#   6. API Gateway + JWT Cognito                              (03-create-api-gateway.sh)
#   7. Alarmas CloudWatch                                     (04-create-alarms.sh)
#   8. Variables de Amplify (VITE_API_URL...) + redeploy del frontend
#
#  Variables opcionales (export antes de ejecutar):
#   DB_PASSWORD      password del RDS (si no, se pregunta)
#   GITHUB_TOKEN     solo si el repo es PRIVADO (Personal Access Token con permiso de lectura)
#   MY_IP            tu IP publica para 22/15672/15673 (si no, se abren a todo el mundo: solo laboratorio)
#   FRONTEND_ORIGIN  URL de tu Amplify
#   RECREATE_API=true  borra y recrea el API Gateway pedidos360-api si ya existe
# =====================================================================================
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
export AWS_REGION="$REGION" AWS_DEFAULT_REGION="$REGION"
REPO_URL="${REPO_URL:-https://github.com/franar22/Pedidos360-Microservicios.git}"
FRONTEND_ORIGIN="${FRONTEND_ORIGIN:-https://360pedidos.d3okqezea05os0.amplifyapp.com}"
DB_HOST="${DB_HOST:-pedidos360-db.cbt0ksx0xrcv.us-east-1.rds.amazonaws.com}"
DB_USER="${DB_USER:-admin}"
COGNITO_POOL="${COGNITO_POOL:-us-east-1_ULZawCXgF}"
COGNITO_CLIENT_ID="${COGNITO_CLIENT_ID:-3t8mlgna7rvekejqb465neqvhv}"
COGNITO_ISSUER="https://cognito-idp.${REGION}.amazonaws.com/${COGNITO_POOL}"
RABBITMQ_USER="${RABBITMQ_USER:-pedidos360}"
RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-Pedidos360Rabbit2026}"
RABBITMQ_ERLANG_COOKIE="${RABBITMQ_ERLANG_COOKIE:-pedidos360-cookie-lab}"
INSTANCE_TYPE="${INSTANCE_TYPE:-t3.medium}"
SG_NAME="pedidos360-sg"

cd "$(dirname "$0")/../.."          # raiz del proyecto
step() { echo; echo "================ $* ================"; }

# ---------------------------------------------------------------- 0. Datos
step "0. Verificando credenciales y datos"
ACCT="$(aws sts get-caller-identity --query Account --output text)" || {
  echo "ERROR: sin credenciales AWS. Inicia el Learner Lab (Start Lab) y abre CloudShell desde esa consola."; exit 1; }
echo "Cuenta: ${ACCT} | Region: ${REGION}"

if [ -z "${DB_PASSWORD:-}" ]; then
  read -r -s -p "Password del RDS (usuario ${DB_USER}): " DB_PASSWORD; echo
fi
if [ -z "$DB_PASSWORD" ] || printf '%s' "$DB_PASSWORD" | grep -q '[^A-Za-z0-9._@#%+=:,!^~-]'; then
  echo "ERROR: la password esta vacia o tiene caracteres que rompen el .env (usa solo letras, numeros y . _ @ # % + = : , ! ^ ~ -)."
  echo "       Si la tuya tiene otros simbolos, cambiala en RDS (Modify) o edita el .env a mano en la EC2."; exit 1
fi

CLONE_URL="$REPO_URL"
if [ -n "${GITHUB_TOKEN:-}" ]; then CLONE_URL="${REPO_URL/https:\/\//https://${GITHUB_TOKEN}@}"; fi

# ---------------------------------------------------------------- 1. SNS / SQS / S3
step "1. SNS + SQS + S3"
bash infra/aws/01-create-sns-sqs.sh
TOPIC_ARN="$(aws sns create-topic --name pedidos360-pedido-eventos --query TopicArn --output text)"
BUCKET="pedidos360-tickets-${ACCT}"

# ---------------------------------------------------------------- 2. Red y RDS
step "2. Security Group y RDS"
VPC_ID="$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)"
SG_ID="$(aws ec2 describe-security-groups --filters "Name=group-name,Values=${SG_NAME}" "Name=vpc-id,Values=${VPC_ID}" \
         --query 'SecurityGroups[0].GroupId' --output text)"
if [ "$SG_ID" = "None" ] || [ -z "$SG_ID" ]; then
  SG_ID="$(aws ec2 create-security-group --group-name "$SG_NAME" --description "Pedidos360 EC2" --vpc-id "$VPC_ID" \
           --query GroupId --output text)"
fi
echo "Security Group: ${SG_ID}"
ADMIN_CIDR="0.0.0.0/0"; [ -n "${MY_IP:-}" ] && ADMIN_CIDR="${MY_IP}/32"
for P in 8081 8082 8083; do
  aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --protocol tcp --port "$P" --cidr 0.0.0.0/0 2>/dev/null || true
done
for P in 22 15672 15673; do
  aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --protocol tcp --port "$P" --cidr "$ADMIN_CIDR" 2>/dev/null || true
done
[ "$ADMIN_CIDR" = "0.0.0.0/0" ] && echo "AVISO: 22/15672/15673 abiertos a todo internet (solo laboratorio). Usa MY_IP=x.x.x.x para restringir."

RDS_ID="$(aws rds describe-db-instances --query "DBInstances[?Endpoint.Address=='${DB_HOST}'].DBInstanceIdentifier | [0]" --output text)"
if [ "$RDS_ID" = "None" ] || [ -z "$RDS_ID" ]; then
  echo "ERROR: no encuentro el RDS ${DB_HOST} en esta cuenta. Si usas otro lab/RDS: export DB_HOST=<endpoint> y repite."; exit 1
fi
RDS_STATUS="$(aws rds describe-db-instances --db-instance-identifier "$RDS_ID" --query 'DBInstances[0].DBInstanceStatus' --output text)"
if [ "$RDS_STATUS" = "stopped" ]; then
  echo "RDS detenido: encendiendolo (tarda unos minutos)..."
  aws rds start-db-instance --db-instance-identifier "$RDS_ID" >/dev/null
fi
aws rds wait db-instance-available --db-instance-identifier "$RDS_ID"
RDS_SG="$(aws rds describe-db-instances --db-instance-identifier "$RDS_ID" --query 'DBInstances[0].VpcSecurityGroups[0].VpcSecurityGroupId' --output text)"
aws ec2 authorize-security-group-ingress --group-id "$RDS_SG" --protocol tcp --port 3306 --source-group "$SG_ID" 2>/dev/null || true
echo "RDS ${RDS_ID} disponible; 3306 permitido desde ${SG_ID}"

# ---------------------------------------------------------------- 3. EC2
step "3. EC2 ${INSTANCE_TYPE}"
INSTANCE_ID="$(aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=pedidos360" "Name=instance-state-name,Values=pending,running,stopped,stopping" \
  --query 'Reservations[].Instances[0].InstanceId | [0]' --output text)"

if [ "$INSTANCE_ID" = "None" ] || [ -z "$INSTANCE_ID" ]; then
  AMI="$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
         --query Parameter.Value --output text)"
  UD="$(mktemp)"
  cat > "$UD" <<USERDATA
#!/bin/bash
export HOME=/root
exec > /var/log/pedidos360-setup.log 2>&1
set -x
dnf install -y docker git
systemctl enable --now docker
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-\$(uname -m)" -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
cd /home/ec2-user
git clone ${CLONE_URL} app
cd app
git remote set-url origin ${REPO_URL}
cat > .env <<ENVEOF
DB_HOST=${DB_HOST}
DB_USER=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}
COGNITO_ISSUER_URI=${COGNITO_ISSUER}
CORS_ALLOWED_ORIGINS=http://localhost:5173,${FRONTEND_ORIGIN}
RABBITMQ_USER=${RABBITMQ_USER}
RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD}
RABBITMQ_ERLANG_COOKIE=${RABBITMQ_ERLANG_COOKIE}
AWS_REGION=${REGION}
AWS_ENABLED=true
SNS_TOPIC_ARN=${TOPIC_ARN}
SQS_AUDITORIA_QUEUE=pedidos360-auditoria
TICKETS_BUCKET=${BUCKET}
ENVEOF
chown -R ec2-user:ec2-user /home/ec2-user/app
docker compose up -d --build
echo "SETUP-TERMINADO"
USERDATA

  INSTANCE_ID="$(aws ec2 run-instances --image-id "$AMI" --instance-type "$INSTANCE_TYPE" \
    --security-group-ids "$SG_ID" --iam-instance-profile Name=LabInstanceProfile \
    --metadata-options "HttpTokens=required,HttpPutResponseHopLimit=2,HttpEndpoint=enabled" \
    --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=20,VolumeType=gp3}' \
    --user-data "file://${UD}" \
    --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=pedidos360}]' \
    --query 'Instances[0].InstanceId' --output text)"
  rm -f "$UD"
  echo "Instancia creada: ${INSTANCE_ID}"
else
  STATE="$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --query 'Reservations[0].Instances[0].State.Name' --output text)"
  [ "$STATE" = "stopped" ] && aws ec2 start-instances --instance-ids "$INSTANCE_ID" >/dev/null
  echo "Reutilizando instancia existente ${INSTANCE_ID} (estado: ${STATE})"
fi
aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"
EC2_HOST="$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --query 'Reservations[0].Instances[0].PublicDnsName' --output text)"
echo "EC2: ${EC2_HOST}"

# ---------------------------------------------------------------- 4. Esperar servicios
step "4. Esperando a que los microservicios arranquen (la primera vez 8-15 min compilando)"
wait_http() {  # $1 url
  for i in $(seq 1 90); do
    if curl -fsS -m 5 "$1" >/dev/null 2>&1; then echo "OK  $1"; return 0; fi
    sleep 10
  done
  echo "TIMEOUT $1  -> entra a la EC2 (EC2 > Connect) y revisa: sudo tail -n 80 /var/log/pedidos360-setup.log ; cd app ; docker compose ps"
  return 1
}
wait_http "http://${EC2_HOST}:8081/api/catalog/health"
wait_http "http://${EC2_HOST}:8082/api/orders/health"
wait_http "http://${EC2_HOST}:8083/api/rabbit/health"

# ---------------------------------------------------------------- 5. Lambda
step "5. Lambda de email"
if ! java -version 2>&1 | grep -q 'version "21'; then
  echo "Instalando Java 21 y Maven en CloudShell..."
  sudo dnf install -y java-21-amazon-corretto-devel maven >/dev/null
  sudo alternatives --set java "$(ls /usr/lib/jvm/java-21-amazon-corretto*/bin/java | head -n1)" 2>/dev/null || true
  export JAVA_HOME="$(ls -d /usr/lib/jvm/java-21-amazon-corretto* | head -n1)"; export PATH="$JAVA_HOME/bin:$PATH"
fi
bash infra/aws/02-create-lambda-email.sh

# ---------------------------------------------------------------- 6. API Gateway
step "6. API Gateway + JWT"
EXISTING_API="$(aws apigatewayv2 get-apis --query "Items[?Name=='pedidos360-api'].ApiId | [0]" --output text)"
if [ "$EXISTING_API" != "None" ] && [ -n "$EXISTING_API" ] && [ "${RECREATE_API:-false}" = "true" ]; then
  aws apigatewayv2 delete-api --api-id "$EXISTING_API"; EXISTING_API="None"
fi
if [ "$EXISTING_API" != "None" ] && [ -n "$EXISTING_API" ]; then
  API_URL="https://${EXISTING_API}.execute-api.${REGION}.amazonaws.com"
  echo "AVISO: ya existe pedidos360-api (${EXISTING_API}); se reutiliza. Si apuntaba a otra EC2, repite con RECREATE_API=true."
else
  API_OUT="$(EC2_HOST="$EC2_HOST" FRONTEND_ORIGIN="$FRONTEND_ORIGIN" COGNITO_ISSUER="$COGNITO_ISSUER" \
             COGNITO_CLIENT_ID="$COGNITO_CLIENT_ID" bash infra/aws/03-create-api-gateway.sh | tee /dev/stderr)"
  API_URL="$(echo "$API_OUT" | grep -o 'https://[a-z0-9]*\.execute-api\.[a-z0-9-]*\.amazonaws\.com' | head -n1)"
fi

# ---------------------------------------------------------------- 7. Alarmas
step "7. Alarmas CloudWatch"
bash infra/aws/04-create-alarms.sh

# ---------------------------------------------------------------- 8. Amplify
step "8. Amplify"
HOST="${FRONTEND_ORIGIN#https://}"; BRANCH="${HOST%%.*}"; REST="${HOST#*.}"; APP_ID="${REST%%.*}"
if aws amplify get-app --app-id "$APP_ID" >/dev/null 2>&1; then
  CUR="$(aws amplify get-app --app-id "$APP_ID" --query 'app.environmentVariables' --output json)"
  NEW="$(python3 - "$CUR" "$API_URL" "$COGNITO_ISSUER" "$COGNITO_CLIENT_ID" <<'PY'
import json, sys
env = json.loads(sys.argv[1]) or {}
env.update({"VITE_API_URL": sys.argv[2], "VITE_COGNITO_AUTHORITY": sys.argv[3], "VITE_COGNITO_CLIENT_ID": sys.argv[4]})
print(json.dumps(env))
PY
)"
  aws amplify update-app --app-id "$APP_ID" --environment-variables "$NEW" >/dev/null
  aws amplify start-job --app-id "$APP_ID" --branch-name "$BRANCH" --job-type RELEASE >/dev/null \
    && echo "Amplify actualizado y redeploy iniciado (app ${APP_ID}, rama ${BRANCH})."
else
  echo "No pude actualizar Amplify automaticamente. Hazlo a mano: Amplify > tu app > Environment variables:"
  echo "   VITE_API_URL=${API_URL}"
  echo "   VITE_COGNITO_AUTHORITY=${COGNITO_ISSUER}"
  echo "   VITE_COGNITO_CLIENT_ID=${COGNITO_CLIENT_ID}"
  echo "y pulsa Redeploy this version."
fi

cat <<OUT

=====================================================================
 LISTO
   Frontend:           ${FRONTEND_ORIGIN}   (espera 2-3 min a que Amplify termine)
   API Gateway:        ${API_URL}
   EC2:                ${EC2_HOST}
   Consola RabbitMQ:   http://${EC2_HOST}:15672   (usuario ${RABBITMQ_USER})
   Salud API:          curl -i ${API_URL}/api/orders/health
 Para ahorrar credito: deten la EC2 y el RDS cuando no trabajes.
=====================================================================
OUT