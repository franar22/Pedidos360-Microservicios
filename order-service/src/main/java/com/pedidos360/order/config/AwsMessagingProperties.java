package com.pedidos360.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres/ARNs de recursos AWS (SNS, SQS, S3). Se leen de application.yml / variables de entorno.
 * enabled=false permite correr el servicio sin tocar AWS (solo RabbitMQ).
 */
@ConfigurationProperties(prefix = "pedidos360.aws")
public record AwsMessagingProperties(boolean enabled, Sns sns, Sqs sqs, S3 s3) {

    public record Sns(String topicArn) {}

    public record Sqs(String auditoriaQueue) {}

    public record S3(String ticketsBucket) {}
}
