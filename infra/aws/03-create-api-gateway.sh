#!/usr/bin/env bash
# Crea un API Gateway HTTP API con:
#   - Authorizer JWT (Cognito): rechaza peticiones sin token o con token invalido
#   - Rutas por microservicio hacia la EC2 donde corre docker-compose
#       /api/catalog/**  -> :8081   |   /api/orders/**  -> :8082   |   /api/rabbit/**  -> :8083
#   - CORS para el frontend (Amplify)
# Variables obligatorias:
#   EC2_HOST          DNS o IP publica de la EC2 (ej. ec2-1-2-3-4.compute-1.amazonaws.com)
#   FRONTEND_ORIGIN   URL del frontend (ej. https://main.dxxxx.amplifyapp.com)
# Opcionales (valores de tu Prueba 1 por defecto):
#   COGNITO_ISSUER, COGNITO_CLIENT_ID, AWS_REGION
# Ejemplo:
#   EC2_HOST=ec2-1-2-3-4.compute-1.amazonaws.com FRONTEND_ORIGIN=https://360pedidos.d3okqezea05os0.amplifyapp.com \
#     bash infra/aws/03-create-api-gateway.sh
set -euo pipefail

: "${EC2_HOST:?Define EC2_HOST}"
: "${FRONTEND_ORIGIN:?Define FRONTEND_ORIGIN}"
REGION="${AWS_REGION:-us-east-1}"
ISSUER="${COGNITO_ISSUER:-https://cognito-idp.us-east-1.amazonaws.com/us-east-1_ULZawCXgF}"
CLIENT_ID="${COGNITO_CLIENT_ID:-3t8mlgna7rvekejqb465neqvhv}"

CORS="{\"AllowOrigins\":[\"${FRONTEND_ORIGIN}\",\"http://localhost:5173\"],\"AllowMethods\":[\"GET\",\"POST\",\"PUT\",\"PATCH\",\"DELETE\",\"OPTIONS\"],\"AllowHeaders\":[\"authorization\",\"content-type\"],\"MaxAge\":86400}"

API_ID="$(aws apigatewayv2 create-api --name pedidos360-api --protocol-type HTTP \
  --cors-configuration "$CORS" --region "$REGION" --query ApiId --output text)"
echo ">> API creada: ${API_ID}"

AUTH_ID="$(aws apigatewayv2 create-authorizer --api-id "$API_ID" --name cognito-jwt \
  --authorizer-type JWT --identity-source '$request.header.Authorization' \
  --jwt-configuration "Audience=${CLIENT_ID},Issuer=${ISSUER}" \
  --region "$REGION" --query AuthorizerId --output text)"
echo ">> Authorizer JWT: ${AUTH_ID}"

integration() {   # $1 = uri destino
  aws apigatewayv2 create-integration --api-id "$API_ID" --integration-type HTTP_PROXY \
    --integration-method ANY --integration-uri "$1" --payload-format-version 1.0 \
    --region "$REGION" --query IntegrationId --output text
}

route() {         # $1 = route-key  $2 = integration-id  $3 = secured(true|false)
  if [ "$3" = "true" ]; then
    aws apigatewayv2 create-route --api-id "$API_ID" --route-key "$1" --target "integrations/$2" \
      --authorization-type JWT --authorizer-id "$AUTH_ID" --region "$REGION" >/dev/null
  else
    aws apigatewayv2 create-route --api-id "$API_ID" --route-key "$1" --target "integrations/$2" \
      --authorization-type NONE --region "$REGION" >/dev/null
  fi
}

add_service() {   # $1 = base (/api/catalog)  $2 = puerto
  local base="$1" port="$2" i_base i_proxy i_health
  i_base="$(integration "http://${EC2_HOST}:${port}${base}")"
  i_proxy="$(integration "http://${EC2_HOST}:${port}${base}/{proxy}")"
  i_health="$(integration "http://${EC2_HOST}:${port}${base}/health")"
  route "GET ${base}/health" "$i_health" false
  route "ANY ${base}" "$i_base" true
  route "ANY ${base}/{proxy+}" "$i_proxy" true
  echo ">> Rutas ${base} -> ${EC2_HOST}:${port}"
}

add_service /api/catalog 8081
add_service /api/orders 8082
add_service /api/rabbit 8083

aws apigatewayv2 create-stage --api-id "$API_ID" --stage-name '$default' --auto-deploy --region "$REGION" >/dev/null

cat <<OUT

=====================================================================
 URL del API Manager (poner en el frontend como VITE_API_URL):
   https://${API_ID}.execute-api.${REGION}.amazonaws.com

 Probar (sin token debe dar 401):
   curl -i https://${API_ID}.execute-api.${REGION}.amazonaws.com/api/orders
 Salud publica:
   curl -i https://${API_ID}.execute-api.${REGION}.amazonaws.com/api/orders/health
=====================================================================
OUT
