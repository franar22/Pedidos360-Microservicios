package com.pedidos360.order.messaging.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.order.event.OrderEvent;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Convierte el cuerpo de un mensaje (RabbitMQ o SQS) en OrderEvent y valida los campos obligatorios.
 * Un mensaje corrupto lanza NonRecoverableMessageException (no tiene sentido reintentar -> DLQ).
 * Si el mensaje de SQS llega con el "sobre" de SNS ({"Type":"Notification","Message":"..."}), lo desenvuelve.
 */
@Component
public class OrderEventParser {

    private final ObjectMapper objectMapper;

    public OrderEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OrderEvent parse(byte[] body) {
        try {
            return validate(objectMapper.readValue(body, OrderEvent.class));
        } catch (IOException ex) {
            throw new NonRecoverableMessageException("JSON invalido: " + ex.getMessage(), ex);
        }
    }

    public OrderEvent parse(String body) {
        try {
            return validate(objectMapper.readValue(unwrapSnsEnvelope(body), OrderEvent.class));
        } catch (IOException ex) {
            throw new NonRecoverableMessageException("JSON invalido: " + ex.getMessage(), ex);
        }
    }

    private String unwrapSnsEnvelope(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (root != null && root.hasNonNull("Type") && "Notification".equals(root.get("Type").asText())
                && root.hasNonNull("Message")) {
            return root.get("Message").asText();
        }
        return body;
    }

    private OrderEvent validate(OrderEvent event) {
        if (event == null || event.eventId() == null || event.orderId() == null || event.status() == null) {
            throw new NonRecoverableMessageException("Faltan campos obligatorios (eventId, orderId, status)");
        }
        return event;
    }
}
