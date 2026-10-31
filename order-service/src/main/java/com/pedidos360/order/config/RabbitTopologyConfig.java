package com.pedidos360.order.config;

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
 * Topologia RabbitMQ de order-service (Spring AMQP la declara sola al conectar).
 *
 *  Exchange DIRECT  pedidos360.direct --[ticket.generar]--> pedidos360.tickets.q         (comando: generar ticket)
 *  Exchange TOPIC   pedidos360.topic  --[pedido.#]--------> pedidos360.notificaciones.q  (eventos del pedido)
 *  Dead letter      pedidos360.dlx    --[<nombre de la cola>]--> <cola>.dlq   (una DLQ por cada cola)
 *
 * Un bean Queue / Exchange / Binding por cada caso de uso, sin nombres escritos a mano:
 * todo viene de RabbitNamesProperties (application.yml).
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
    public DirectExchange directExchange() {
        return new DirectExchange(names.exchanges().direct(), true, false);
    }

    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(names.exchanges().topic(), true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(names.exchanges().dlx(), true, false);
    }

    // ---------------- Caso de uso 1: notificaciones (topic) ----------------

    @Bean
    public Queue notificacionesQueue() {
        return QueueBuilder.durable(names.queues().notificaciones())
                .deadLetterExchange(names.exchanges().dlx())
                .deadLetterRoutingKey(names.queues().notificaciones())
                .build();
    }

    @Bean
    public Queue notificacionesDlq() {
        return QueueBuilder.durable(names.dlqOf(names.queues().notificaciones())).build();
    }

    @Bean
    public Binding notificacionesBinding(Queue notificacionesQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(notificacionesQueue).to(topicExchange)
                .with(names.routingKeys().notificacionesBinding());
    }

    @Bean
    public Binding notificacionesDlqBinding(Queue notificacionesDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(notificacionesDlq).to(deadLetterExchange).with(names.queues().notificaciones());
    }

    // ---------------- Caso de uso 2: tickets (direct) ----------------

    @Bean
    public Queue ticketsQueue() {
        return QueueBuilder.durable(names.queues().tickets())
                .deadLetterExchange(names.exchanges().dlx())
                .deadLetterRoutingKey(names.queues().tickets())
                .build();
    }

    @Bean
    public Queue ticketsDlq() {
        return QueueBuilder.durable(names.dlqOf(names.queues().tickets())).build();
    }

    @Bean
    public Binding ticketsBinding(Queue ticketsQueue, DirectExchange directExchange) {
        return BindingBuilder.bind(ticketsQueue).to(directExchange).with(names.routingKeys().ticket());
    }

    @Bean
    public Binding ticketsDlqBinding(Queue ticketsDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(ticketsDlq).to(deadLetterExchange).with(names.queues().tickets());
    }
}
