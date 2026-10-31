package com.pedidos360.order.messaging.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.order.config.RabbitNamesProperties;
import com.pedidos360.order.event.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * PRODUCTOR RabbitMQ de pedidos.
 *  - Todo evento -> exchange TOPIC con routing key  pedido.creado  |  pedido.estado.<estado>
 *      (lo recibe la cola de notificaciones por el binding "pedido.#")
 *  - Pedido ACEPTADO -> ademas exchange DIRECT con routing key ticket.generar (cola de tickets)
 * Los mensajes son persistentes (sobreviven a un reinicio del broker) y llevan messageId = eventId.
 */
@Component
@ConditionalOnProperty(prefix = "pedidos360.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitOrderPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitOrderPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final RabbitNamesProperties names;

    public RabbitOrderPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper, RabbitNamesProperties names) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.names = names;
    }

    public void publish(OrderEvent event) {
        try {
            Message message = MessageBuilder
                    .withBody(objectMapper.writeValueAsBytes(event))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(event.eventId())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();

            String topicKey = topicRoutingKey(event);
            rabbitTemplate.send(names.exchanges().topic(), topicKey, message);
            log.info("RabbitMQ publicado exchange={} routingKey={} pedido={}",
                    names.exchanges().topic(), topicKey, event.orderId());

            if ("ACEPTADO".equals(event.status()) && OrderEvent.TYPE_STATUS_CHANGED.equals(event.type())) {
                rabbitTemplate.send(names.exchanges().direct(), names.routingKeys().ticket(), message);
                log.info("RabbitMQ publicado exchange={} routingKey={} pedido={}",
                        names.exchanges().direct(), names.routingKeys().ticket(), event.orderId());
            }
        } catch (Exception ex) {
            log.error("No se pudo publicar en RabbitMQ pedido={} (el pedido igual quedo guardado)", event.orderId(), ex);
        }
    }

    private String topicRoutingKey(OrderEvent event) {
        if (OrderEvent.TYPE_CREATED.equals(event.type())) {
            return names.routingKeys().pedidoCreado();
        }
        return names.routingKeys().pedidoEstadoPrefix() + event.status().toLowerCase();
    }
}
