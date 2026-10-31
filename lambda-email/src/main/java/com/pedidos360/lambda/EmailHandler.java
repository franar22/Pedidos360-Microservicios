package com.pedidos360.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumidor serverless del fan-out SNS -> SQS (cola pedidos360-email).
 * Handler en Lambda:  com.pedidos360.lambda.EmailHandler::handleRequest   (Java 21)
 *
 * - Acepta el mensaje con sobre SNS ({"Type":"Notification","Message":"{...}"}) o crudo (raw delivery).
 * - Devuelve SQSBatchResponse: solo los mensajes que fallaron vuelven a la cola
 *   (activar "Report batch item failures" en el trigger SQS).
 * - En el laboratorio "enviar el correo" = escribir en CloudWatch Logs (SES requiere identidades verificadas).
 */
public class EmailHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        for (SQSEvent.SQSMessage msg : event.getRecords()) {
            try {
                JsonNode pedido = unwrap(msg.getBody());
                String status = text(pedido, "status");
                String email = text(pedido, "customerEmail");
                String orderId = text(pedido, "orderId");
                if (orderId == null || status == null) {
                    throw new IllegalArgumentException("Evento sin orderId/status");
                }
                context.getLogger().log("EMAIL -> " + email + " | Pedido #" + orderId
                        + " ahora esta " + status + " (total " + text(pedido, "total") + ")");
            } catch (Exception e) {
                context.getLogger().log("ERROR procesando messageId=" + msg.getMessageId() + ": " + e.getMessage());
                failures.add(new SQSBatchResponse.BatchItemFailure(msg.getMessageId()));
            }
        }
        return new SQSBatchResponse(failures);
    }

    private JsonNode unwrap(String body) throws Exception {
        JsonNode root = JSON.readTree(body);
        if (root.hasNonNull("Type") && "Notification".equals(root.get("Type").asText()) && root.hasNonNull("Message")) {
            return JSON.readTree(root.get("Message").asText());
        }
        return root;
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
