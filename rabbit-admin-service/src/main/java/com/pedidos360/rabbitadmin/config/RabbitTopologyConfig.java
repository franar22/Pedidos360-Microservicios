package com.pedidos360.rabbitadmin.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia BASE de la plataforma (la declara este servicio al arrancar, de forma idempotente):
 *   2 exchanges (direct + topic) + exchange de dead letters + 3 colas, cada una con su DLQ.
 * Es la misma que declaran order-service y catalog-service (mismos nombres y argumentos, todo desde application.yml).
 * Sobre esta base, la API REST permite crear/eliminar colas, exchanges y bindings adicionales en caliente.
 */
@Configuration
public class RabbitTopologyConfig {

    private final RabbitNamesProperties names;

    public RabbitTopologyConfig(RabbitNamesProperties names) {
        this.names = names;
    }

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

    // ---- notificaciones (topic) ----
    @Bean
    public Queue notificacionesQueue() {
        return mainQueue(names.queues().notificaciones());
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

    // ---- tickets (direct) ----
    @Bean
    public Queue ticketsQueue() {
        return mainQueue(names.queues().tickets());
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

    // ---- inventario (topic) ----
    @Bean
    public Queue inventarioQueue() {
        return mainQueue(names.queues().inventario());
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

    private Queue mainQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(names.exchanges().dlx())
                .deadLetterRoutingKey(name)
                .build();
    }
}
