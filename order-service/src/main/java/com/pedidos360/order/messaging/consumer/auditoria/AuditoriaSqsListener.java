package com.pedidos360.order.messaging.consumer.auditoria;

import com.pedidos360.order.event.OrderEvent;
import com.pedidos360.order.messaging.support.OrderEventParser;
import com.pedidos360.order.model.OrderAuditLog;
import com.pedidos360.order.repository.OrderAuditLogRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * CONSUMIDOR SQS (Amazon SQS, suscrita al topico SNS pedido-eventos): guarda la bitacora de cada evento.
 * - Cola: viene de application.yml (pedidos360.aws.sqs.auditoria-queue)
 * - ACK automatico "ON_SUCCESS" de Spring Cloud AWS: el mensaje se borra solo si el metodo termina sin excepcion.
 *   Si lanza excepcion vuelve a la cola tras el visibility timeout y, superado maxReceiveCount, SQS lo mueve
 *   a la DLQ (redrive policy creada por infra/aws/01-create-sns-sqs.sh).
 * - Idempotente: event_id UNIQUE evita duplicar la bitacora si SQS Standard entrega dos veces (at-least-once).
 * Solo se activa con AWS_ENABLED=true.
 */
@Component
@ConditionalOnProperty(prefix = "pedidos360", name = {"messaging.enabled", "messaging.consumers-enabled", "aws.enabled"},
        havingValue = "true")
public class AuditoriaSqsListener {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaSqsListener.class);

    private final OrderEventParser parser;
    private final OrderAuditLogRepository auditLogRepository;

    public AuditoriaSqsListener(OrderEventParser parser, OrderAuditLogRepository auditLogRepository) {
        this.parser = parser;
        this.auditLogRepository = auditLogRepository;
    }

    @SqsListener("${pedidos360.aws.sqs.auditoria-queue}")
    public void onMessage(String body) {
        OrderEvent event = parser.parse(body);

        if (auditLogRepository.existsByEventId(event.eventId())) {
            log.info("Auditoria: eventId={} ya registrado, se ignora el duplicado", event.eventId());
            return;
        }

        OrderAuditLog entry = new OrderAuditLog();
        entry.setEventId(event.eventId());
        entry.setOrderId(event.orderId());
        entry.setEventType(event.type());
        entry.setPreviousStatus(event.previousStatus());
        entry.setStatus(event.status());

        try {
            auditLogRepository.save(entry);
            log.info("Auditoria: pedido={} {} -> {}", event.orderId(), event.previousStatus(), event.status());
        } catch (DataIntegrityViolationException duplicate) {
            log.info("Auditoria: eventId={} duplicado concurrente, se ignora", event.eventId());
        }
    }
}
