package com.pedidos360.catalog.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.catalog.config.RabbitNamesProperties;
import com.pedidos360.catalog.service.LowStockDetected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * PRODUCTOR RabbitMQ del catalogo. Publica en el exchange TOPIC con routing key "inventario.stock.bajo".
 * Se ejecuta DESPUES del commit de la reserva: si RabbitMQ esta caido la reserva de stock igual se confirma
 * (la mensajeria nunca rompe la logica de negocio existente) y el fallo queda en logs.
 */
@Component
@ConditionalOnProperty(prefix = "pedidos360.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StockEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(StockEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final RabbitNamesProperties names;

    public StockEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper, RabbitNamesProperties names) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.names = names;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLowStock(LowStockDetected detected) {
        try {
            StockAlertEvent event = new StockAlertEvent(
                    UUID.randomUUID().toString(), StockAlertEvent.TYPE, 1, Instant.now(),
                    detected.productId(), detected.productName(), detected.stock(), detected.threshold());

            Message message = MessageBuilder
                    .withBody(objectMapper.writeValueAsBytes(event))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(event.eventId())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();

            rabbitTemplate.send(names.exchanges().topic(), names.routingKeys().stockBajo(), message);
            log.info("Evento StockBajo publicado productId={} stock={} exchange={} routingKey={}",
                    detected.productId(), detected.stock(), names.exchanges().topic(), names.routingKeys().stockBajo());
        } catch (Exception ex) {
            log.error("No se pudo publicar StockBajo productId={} (la reserva igual quedo confirmada)",
                    detected.productId(), ex);
        }
    }
}
