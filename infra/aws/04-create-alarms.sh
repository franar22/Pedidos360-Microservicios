#!/usr/bin/env bash
# Alarmas de CloudWatch: se activan si hay mensajes en alguna DLQ de SQS (> 0 durante 1 minuto).
# Opcional: ALERT_TOPIC_ARN=arn:aws:sns:... para recibir el aviso por correo.
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
for Q in auditoria email; do
  ACTIONS=()
  if [ -n "${ALERT_TOPIC_ARN:-}" ]; then ACTIONS=(--alarm-actions "$ALERT_TOPIC_ARN"); fi
  aws cloudwatch put-metric-alarm \
    --alarm-name "pedidos360-${Q}-dlq-no-vacia" \
    --namespace AWS/SQS --metric-name ApproximateNumberOfMessagesVisible \
    --dimensions "Name=QueueName,Value=pedidos360-${Q}-dlq" \
    --statistic Maximum --period 60 --evaluation-periods 1 \
    --threshold 0 --comparison-operator GreaterThanThreshold \
    --treat-missing-data notBreaching \
    "${ACTIONS[@]}" --region "$REGION"
  echo ">> Alarma pedidos360-${Q}-dlq-no-vacia creada"
done
