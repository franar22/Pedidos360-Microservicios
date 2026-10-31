package com.pedidos360.catalog.messaging.consumer.inventario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.catalog.messaging.StockAlertEvent;
import com.pedidos360.catalog.messaging.support.NonRecoverableMessageException;
import com.pedidos360.catalog.messaging.support.ReliableMessageProcessor;
import com.pedidos360.catalog.model.StockAlert;
import com.pedidos360.catalog.repository.StockAlertRepository;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CONSUMIDOR del dominio "inventario": registra las alertas de stock bajo.
 * - Cola: pedidos360.inventario.q (viene de application.yml, no esta escrita aqui)
 * - ACK manual a traves de ReliableMessageProcessor (ACK / reintentos / DLQ)
 * - Idempotente: si el eventId ya fue procesado, se confirma el mensaje sin duplicar la alerta.
 */
@Component
@ConditionalOnProperty(prefix = "pedidos360.messaging", name = {"enabled", "consumers-enabled"},
        havingValue = "true", matchIfMissing = true)
public class StockAlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(StockAlertConsumer.class);

    private final ReliableMessageProcessor processor;
    private final StockAlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    public StockAlertConsumer(ReliableMessageProcessor processor,
                              StockAlertRepository alertRepository,
                              ObjectMapper objectMapper) {
        this.processor = processor;
        this.alertRepository = alertRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${pedidos360.rabbit.queues.inventario}")
    public void onMessage(Message message, Channel channel) throws IOException {
        processor.process(message, channel, this::handle);
    }

    private void handle(Message message) {
        StockAlertEvent event = parse(message);

        if (alertRepository.existsByEventId(event.eventId())) {
            log.info("StockBajo eventId={} ya procesado, se ignora el duplicado", event.eventId());
            return;
        }

        StockAlert alert = new StockAlert();
        alert.setEventId(event.eventId());
        alert.setProductId(event.productId());
        alert.setProductName(event.productName());
        alert.setStock(event.stock());
        alert.setThreshold(event.threshold());
        alertRepository.save(alert);

        log.warn("ALERTA STOCK BAJO producto={} ({}) stock={} umbral={}",
                event.productName(), event.productId(), event.stock(), event.threshold());
    }

    private StockAlertEvent parse(Message message) {
        try {
            StockAlertEvent event = objectMapper.readValue(message.getBody(), StockAlertEvent.class);
            if (event.eventId() == null || event.productId() == null || event.productName() == null) {
                throw new NonRecoverableMessageException("Faltan campos obligatorios (eventId, productId, productName)");
            }
            return event;
        } catch (IOException ex) {
            throw new NonRecoverableMessageException("JSON invalido: " + ex.getMessage(), ex);
        }
    }
}
