package com.pedidos360.catalog.messaging.support;

import com.pedidos360.catalog.config.RabbitNamesProperties;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Politica unica de confirmacion (ACK) para TODOS los consumidores del servicio:
 *
 *   procesa OK .......................... basicAck
 *   NonRecoverableMessageException ...... basicNack(requeue=false) -> DLQ (sin reintentos)
 *   otra excepcion (recuperable) ........ reintenta N veces con backoff; si se agotan -> basicNack(requeue=false) -> DLQ
 *   el servicio se esta apagando ........ basicNack(requeue=true) para no perder el mensaje
 *
 * Asi nunca hay requeue infinito ("mensaje veneno") y cada mensaje fallido queda registrado en logs
 * y almacenado en la DLQ para revision/redrive.
 */
@Component
public class ReliableMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReliableMessageProcessor.class);

    private final RabbitNamesProperties names;
    private final int maxAttempts;
    private final long backoffMs;

    public ReliableMessageProcessor(RabbitNamesProperties names,
                                    @Value("${pedidos360.rabbit.consumer.max-attempts}") int maxAttempts,
                                    @Value("${pedidos360.rabbit.consumer.backoff-ms}") long backoffMs) {
        this.names = names;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMs = Math.max(0, backoffMs);
    }

    public void process(Message message, Channel channel, MessageHandler handler) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();
        String queue = message.getMessageProperties().getConsumerQueue();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                handler.handle(message);
                channel.basicAck(tag, false);
                log.info("ACK queue={} messageId={} intento={}", queue, messageId, attempt);
                return;
            } catch (NonRecoverableMessageException ex) {
                rejectToDlq(message, channel, tag, queue, messageId, "no recuperable: " + ex.getMessage(), ex);
                return;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Apagando: REQUEUE queue={} messageId={}", queue, messageId);
                channel.basicNack(tag, false, true);
                return;
            } catch (Exception ex) {
                if (attempt >= maxAttempts) {
                    rejectToDlq(message, channel, tag, queue, messageId,
                            "reintentos agotados (" + maxAttempts + "): " + ex.getMessage(), ex);
                    return;
                }
                log.warn("Fallo recuperable queue={} messageId={} intento={}/{}: {}",
                        queue, messageId, attempt, maxAttempts, ex.getMessage());
                try {
                    Thread.sleep(backoffMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    channel.basicNack(tag, false, true);
                    return;
                }
            }
        }
    }

    private void rejectToDlq(Message message, Channel channel, long tag, String queue,
                             String messageId, String reason, Exception cause) throws IOException {
        String dlq = queue == null ? "(dlq)" : names.dlqOf(queue);
        log.error("MENSAJE A DLQ queue={} dlq={} messageId={} motivo={} body={}",
                queue, dlq, messageId, reason, preview(message), cause);
        channel.basicNack(tag, false, false);
    }

    private String preview(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }
}
