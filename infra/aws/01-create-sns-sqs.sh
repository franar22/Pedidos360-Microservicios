#!/usr/bin/env bash
# Crea en AWS (Learner Lab, AWS CloudShell o tu terminal con credenciales):
#   - Topico SNS  pedidos360-pedido-eventos
#   - Colas SQS   pedidos360-auditoria y pedidos360-email, cada una con su DLQ (maxReceiveCount=3)
#   - Politica que permite a SNS escribir en las colas + suscripciones (fan-out)
#   - Bucket S3 para los tickets
# Es idempotente: se puede ejecutar mas de una vez.
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
ACCT="$(aws sts get-caller-identity --query Account --output text)"
TOPIC_NAME="pedidos360-pedido-eventos"
BUCKET="pedidos360-tickets-${ACCT}"
TMP="$(mktemp -d)"

echo ">> Cuenta ${ACCT} | region ${REGION}"

# --- Topico SNS ---
TOPIC_ARN="$(aws sns create-topic --name "$TOPIC_NAME" --region "$REGION" --query TopicArn --output text)"
echo ">> Topico: ${TOPIC_ARN}"

create_queue_with_dlq() {   # $1 = auditoria | email
  local name="$1" dlq_url dlq_arn q_url q_arn
  dlq_url="$(aws sqs create-queue --queue-name "pedidos360-${name}-dlq" --region "$REGION" --query QueueUrl --output text)"
  dlq_arn="$(aws sqs get-queue-attributes --queue-url "$dlq_url" --attribute-names QueueArn --region "$REGION" --query Attributes.QueueArn --output text)"

  python3 - "$TMP/${name}-attrs.json" "$dlq_arn" <<'PY'
import json, sys
out, dlq_arn = sys.argv[1], sys.argv[2]
attrs = {
    "VisibilityTimeout": "60",
    "ReceiveMessageWaitTimeSeconds": "20",
    "RedrivePolicy": json.dumps({"deadLetterTargetArn": dlq_arn, "maxReceiveCount": "3"}),
}
json.dump(attrs, open(out, "w"))
PY
  q_url="$(aws sqs create-queue --queue-name "pedidos360-${name}" --attributes "file://${TMP}/${name}-attrs.json" --region "$REGION" --query QueueUrl --output text)"
  q_arn="$(aws sqs get-queue-attributes --queue-url "$q_url" --attribute-names QueueArn --region "$REGION" --query Attributes.QueueArn --output text)"

  # Permite que el topico SNS escriba en la cola
  python3 - "$TMP/${name}-policy.json" "$q_arn" "$TOPIC_ARN" <<'PY'
import json, sys
out, q_arn, topic_arn = sys.argv[1], sys.argv[2], sys.argv[3]
policy = {
    "Version": "2012-10-17",
    "Statement": [{
        "Effect": "Allow",
        "Principal": {"Service": "sns.amazonaws.com"},
        "Action": "sqs:SendMessage",
        "Resource": q_arn,
        "Condition": {"ArnEquals": {"aws:SourceArn": topic_arn}},
    }],
}
json.dump({"Policy": json.dumps(policy)}, open(out, "w"))
PY
  aws sqs set-queue-attributes --queue-url "$q_url" --attributes "file://${TMP}/${name}-policy.json" --region "$REGION"

  echo "${q_arn}"
}

AUDIT_ARN="$(create_queue_with_dlq auditoria | tail -n1)"
EMAIL_ARN="$(create_queue_with_dlq email | tail -n1)"

# --- Suscripciones (fan-out). auditoria: raw delivery ON (JSON limpio); email: OFF (la Lambda parsea el sobre SNS) ---
aws sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs --notification-endpoint "$AUDIT_ARN" \
  --attributes RawMessageDelivery=true --region "$REGION" --output text >/dev/null
aws sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs --notification-endpoint "$EMAIL_ARN" \
  --region "$REGION" --output text >/dev/null
echo ">> Suscripciones creadas (auditoria raw=ON, email raw=OFF)"

# --- Bucket S3 de tickets ---
if aws s3api head-bucket --bucket "$BUCKET" --region "$REGION" 2>/dev/null; then
  echo ">> Bucket ${BUCKET} ya existe"
else
  aws s3 mb "s3://${BUCKET}" --region "$REGION"
fi

cat <<OUT

=====================================================================
 Listo. Pega esto en tu archivo .env y cambia AWS_ENABLED a true:

 AWS_ENABLED=true
 AWS_REGION=${REGION}
 SNS_TOPIC_ARN=${TOPIC_ARN}
 SQS_AUDITORIA_QUEUE=pedidos360-auditoria
 TICKETS_BUCKET=${BUCKET}

 Luego reinicia: docker compose up -d order-service
 ARN cola email (para la Lambda): ${EMAIL_ARN}
=====================================================================
OUT
