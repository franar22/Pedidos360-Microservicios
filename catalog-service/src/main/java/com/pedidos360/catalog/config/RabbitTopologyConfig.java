package com.pedidos360.catalog.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia RabbitMQ que este microservicio necesita (Spring AMQP la declara sola al conectar).
 *
 *   Caso de uso: alertas de stock bajo
 *     catalog-service --publica--> [pedidos360.topic] --inventario.#--> [pedidos360.inventario.q] --> StockAlertConsumer
 *     si el mensaje falla  -----> [pedidos360.dlx] ------------------> [pedidos360.inventario.q.dlq]
 *
 * La cola y el exchange coinciden con los que declara el resto de los microservicios
 * (mismos argumentos), por lo que es seguro que varios servicios los declaren.
 */
@Configuration
@ConditionalOnProperty(prefix = "pedidos360.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitTopologyConfig {

    private final RabbitNamesProperties names;

    public RabbitTopologyConfig(RabbitNamesProperties names) {
        this.names = names;
    }

    // ---------------- Exchanges ----------------

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(names.exchanges().topic(), true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(names.exchanges().dlx(), true, false);
    }

    // ---------------- Caso de uso: inventario ----------------

    @Bean
    public Queue inventarioQueue() {
        return QueueBuilder.durable(names.queues().inventario())
                .deadLetterExchange(names.exchanges().dlx())
                .deadLetterRoutingKey(names.queues().inventario())
                .build();
    }

    @Bean
    public Queue inventarioDlq() {
        return QueueBuilder.durable(names.dlqOf(names.queues().inventario())).build();
    }

    @Bean
    public Binding inventarioBinding(Queue inventarioQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(inventarioQueue).to(topicExchange).with(names.routingKeys().inventarioBinding());
    }

    @Bean
    public Binding inventarioDlqBinding(Queue inventarioDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(inventarioDlq).to(deadLetterExchange).with(names.queues().inventario());
    }
}
