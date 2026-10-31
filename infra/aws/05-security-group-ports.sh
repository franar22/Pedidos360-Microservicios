#!/usr/bin/env bash
# Abre en el Security Group de la EC2 los puertos de los microservicios (API Gateway no tiene IP fija,
# por eso se abre a 0.0.0.0/0 en el laboratorio) y la consola de RabbitMQ solo para TU IP.
# Uso: SG_ID=sg-0123456789 MY_IP=203.0.113.10 bash infra/aws/05-security-group-ports.sh
set -euo pipefail
: "${SG_ID:?Define SG_ID}"
: "${MY_IP:?Define MY_IP (tu IP publica)}"
REGION="${AWS_REGION:-us-east-1}"

for PORT in 8081 8082 8083; do
  aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --protocol tcp --port "$PORT" --cidr 0.0.0.0/0 --region "$REGION" || true
done
for PORT in 15672 15673 22; do
  aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --protocol tcp --port "$PORT" --cidr "${MY_IP}/32" --region "$REGION" || true
done
echo ">> Puertos abiertos. Recuerda: el SG de RDS debe permitir 3306 desde el SG/IP de la EC2."
