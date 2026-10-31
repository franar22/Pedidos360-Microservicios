package com.pedidos360.order.messaging.consumer.notificacion;

import com.pedidos360.order.event.OrderEvent;
import com.pedidos360.order.messaging.support.OrderEventParser;
import com.pedidos360.order.messaging.support.ReliableMessageProcessor;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CONSUMIDOR del dominio "notificaciones": avisa al cliente cada vez que su pedido se crea o cambia de estado.
 * - Cola: pedidos360.notificaciones.q (del application.yml); recibe lo que llega al exchange TOPIC con "pedido.#"
 * - ACK manual con ReliableMessageProcessor (ACK / reintentos con backoff / DLQ).
 * - En el laboratorio "enviar" = escribir el aviso en el log (SES requiere identidades verificadas).
 * - Prueba de DLQ: si el correo del cliente termina en el dominio configurado en pedidos360.demo.failure-email-domain
 *   se simula una caida del proveedor de correo -> 3 reintentos -> DLQ.
 */
@Component
@ConditionalOnProperty(prefix = "pedidos360.messaging", name = {"enabled", "consumers-enabled"},
        havingValue = "true", matchIfMissing = true)
public class NotificacionConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionConsumer.class);

    private final ReliableMessageProcessor processor;
    private final OrderEventParser parser;
    private final String failureEmailDomain;

    public NotificacionConsumer(ReliableMessageProcessor processor,
                                OrderEventParser parser,
                                @Value("${pedidos360.demo.failure-email-domain:}") String failureEmailDomain) {
        this.processor = processor;
        this.parser = parser;
        this.failureEmailDomain = failureEmailDomain;
    }

    @RabbitListener(queues = "${pedidos360.rabbit.queues.notificaciones}")
    public void onMessage(Message message, Channel channel) throws IOException {
        processor.process(message, channel, this::handle);
    }

    private void handle(Message message) {
        OrderEvent event = parser.parse(message.getBody());

        if (!failureEmailDomain.isBlank() && event.customerEmail() != null
                && event.customerEmail().endsWith("@" + failureEmailDomain)) {
            throw new IllegalStateException("Proveedor de correo no disponible (fallo simulado para probar la DLQ)");
        }

        log.info("NOTIFICACION -> {} | Pedido #{} paso a estado {} (total {})",
                event.customerEmail(), event.orderId(), event.status(), event.total());
    }
}
