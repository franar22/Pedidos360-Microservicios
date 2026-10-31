#!/usr/bin/env bash
# Compila y despliega la Lambda consumidora de pedidos360-email y la conecta a la cola SQS
# (event source mapping, batch 10, "Report batch item failures").
# Ejecutar desde la raiz del proyecto con Maven + Java 21 y credenciales AWS activas.
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
ACCT="$(aws sts get-caller-identity --query Account --output text)"
FUNCTION="pedidos360-email"
ROLE_ARN="arn:aws:iam::${ACCT}:role/LabRole"      # Learner Lab: no se pueden crear roles, se usa LabRole
QUEUE_ARN="arn:aws:sqs:${REGION}:${ACCT}:pedidos360-email"

( cd lambda-email && mvn -q -B clean package )
JAR="lambda-email/target/lambda-email-1.0.0.jar"

if aws lambda get-function --function-name "$FUNCTION" --region "$REGION" >/dev/null 2>&1; then
  echo ">> Actualizando codigo de ${FUNCTION}"
  aws lambda update-function-code --function-name "$FUNCTION" --zip-file "fileb://${JAR}" --region "$REGION" >/dev/null
else
  echo ">> Creando ${FUNCTION}"
  aws lambda create-function \
    --function-name "$FUNCTION" \
    --runtime java21 \
    --handler com.pedidos360.lambda.EmailHandler::handleRequest \
    --role "$ROLE_ARN" \
    --zip-file "fileb://${JAR}" \
    --memory-size 512 \
    --timeout 10 \
    --region "$REGION" >/dev/null
  aws lambda wait function-active-v2 --function-name "$FUNCTION" --region "$REGION"

  aws lambda create-event-source-mapping \
    --function-name "$FUNCTION" \
    --event-source-arn "$QUEUE_ARN" \
    --batch-size 10 \
    --function-response-types ReportBatchItemFailures \
    --region "$REGION" >/dev/null
  echo ">> Trigger SQS creado (batch 10, ReportBatchItemFailures)"
fi
echo ">> Listo. Logs en CloudWatch: /aws/lambda/${FUNCTION}"
