package com.pedidos360.order.messaging.consumer.ticket;

import com.pedidos360.order.event.OrderEvent;
import com.pedidos360.order.messaging.support.OrderEventParser;
import com.pedidos360.order.messaging.support.ReliableMessageProcessor;
import com.pedidos360.order.service.TicketService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CONSUMIDOR del dominio "tickets": genera el comprobante del pedido en segundo plano
 * (la respuesta HTTP de "aceptar pedido" no espera a que el documento exista).
 * - Cola: pedidos360.tickets.q (exchange DIRECT, routing key ticket.generar)
 * - Idempotente (un ticket por pedido) y con ACK manual / DLQ via ReliableMessageProcessor.
 */
@Component
@ConditionalOnProperty(prefix = "pedidos360.messaging", name = {"enabled", "consumers-enabled"},
        havingValue = "true", matchIfMissing = true)
public class TicketConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketConsumer.class);

    private final ReliableMessageProcessor processor;
    private final OrderEventParser parser;
    private final TicketService ticketService;

    public TicketConsumer(ReliableMessageProcessor processor, OrderEventParser parser, TicketService ticketService) {
        this.processor = processor;
        this.parser = parser;
        this.ticketService = ticketService;
    }

    @RabbitListener(queues = "${pedidos360.rabbit.queues.tickets}")
    public void onMessage(Message message, Channel channel) throws IOException {
        processor.process(message, channel, this::handle);
    }

    private void handle(Message message) {
        OrderEvent event = parser.parse(message.getBody());

        if (!"ACEPTADO".equals(event.status())) {
            log.info("Pedido {} en estado {}: no requiere ticket, se confirma el mensaje", event.orderId(), event.status());
            return;
        }
        ticketService.generate(event);
    }
}
