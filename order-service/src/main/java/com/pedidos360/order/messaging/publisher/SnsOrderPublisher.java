package com.pedidos360.order.messaging.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.order.config.AwsMessagingProperties;
import com.pedidos360.order.event.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

/**
 * PRODUCTOR SNS: publica una sola vez en el topico "pedido-eventos"; SNS entrega una copia a cada cola SQS
 * suscrita (fan-out: auditoria, email...). Agregar un nuevo proceso = nueva suscripcion, cero cambios aqui.
 * El atributo "type" permite filter policies por tipo de evento.
 */
@Component
@ConditionalOnProperty(prefix = "pedidos360.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SnsOrderPublisher {

    private static final Logger log = LoggerFactory.getLogger(SnsOrderPublisher.class);

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final AwsMessagingProperties aws;

    public SnsOrderPublisher(SnsClient snsClient, ObjectMapper objectMapper, AwsMessagingProperties aws) {
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.aws = aws;
    }

    public void publish(OrderEvent event) {
        String topicArn = aws.sns() == null ? null : aws.sns().topicArn();
        if (!aws.enabled() || topicArn == null || topicArn.isBlank()) {
            log.debug("SNS desactivado (AWS_ENABLED=false o sin SNS_TOPIC_ARN): no se publica pedido={}", event.orderId());
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            snsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(json)
                    .messageAttributes(Map.of("type", MessageAttributeValue.builder()
                            .dataType("String").stringValue(event.type()).build()))
                    .build());
            log.info("SNS publicado topic={} type={} pedido={}", topicArn, event.type(), event.orderId());
        } catch (Exception ex) {
            log.error("No se pudo publicar en SNS pedido={} (el pedido igual quedo guardado)", event.orderId(), ex);
        }
    }
}
