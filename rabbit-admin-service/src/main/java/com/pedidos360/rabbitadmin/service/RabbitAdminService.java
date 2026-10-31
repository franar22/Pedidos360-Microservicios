package com.pedidos360.rabbitadmin.service;

import com.pedidos360.rabbitadmin.config.RabbitNamesProperties;
import com.pedidos360.rabbitadmin.dto.BindingRequest;
import com.pedidos360.rabbitadmin.dto.ExchangeRequest;
import com.pedidos360.rabbitadmin.dto.PublishRequest;
import com.pedidos360.rabbitadmin.dto.QueueRequest;
import com.pedidos360.rabbitadmin.exception.ConflictException;
import com.pedidos360.rabbitadmin.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UNICO punto donde se crea/elimina infraestructura RabbitMQ. Los controladores solo llaman a estos metodos
 * de alto nivel y no conocen RabbitAdmin ni AMQP.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final RabbitAdmin rabbitAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitManagementClient management;
    private final RabbitNamesProperties names;

    public RabbitAdminService(RabbitAdmin rabbitAdmin,
                              RabbitTemplate rabbitTemplate,
                              RabbitManagementClient management,
                              RabbitNamesProperties names) {
        this.rabbitAdmin = rabbitAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.management = management;
        this.names = names;
    }

    // ===================== COLAS =====================

    public QueueInformation createQueue(QueueRequest request) {
        String name = request.getName();
        assertNotReserved(name);
        if (rabbitAdmin.getQueueInfo(name) != null) {
            throw new ConflictException("La cola '" + name + "' ya existe");
        }

        Map<String, Object> args = new HashMap<>();
        if (request.isWithDeadLetter()) {
            args.put("x-dead-letter-exchange", names.exchanges().dlx());
            args.put("x-dead-letter-routing-key", name);
        }
        if (request.getMessageTtlMs() != null) {
            args.put("x-message-ttl", request.getMessageTtlMs());
        }
        if (request.getMaxLength() != null) {
            args.put("x-max-length", request.getMaxLength());
        }

        if (request.isWithDeadLetter()) {
            declareDeadLetterInfrastructure(name);
        }
        rabbitAdmin.declareQueue(new Queue(name, request.isDurable(), false, request.isAutoDelete(), args));
        log.info("Cola creada name={} dlq={}", name, request.isWithDeadLetter() ? names.dlqOf(name) : "no");
        return rabbitAdmin.getQueueInfo(name);
    }

    public QueueInformation getQueue(String name) {
        QueueInformation info = rabbitAdmin.getQueueInfo(name);
        if (info == null) {
            throw new NotFoundException("La cola '" + name + "' no existe");
        }
        return info;
    }

    public List<Map<String, Object>> listQueues() {
        return management.listQueues();
    }

    public void deleteQueue(String name, boolean ifUnused, boolean ifEmpty, boolean withDlq) {
        assertNotReserved(name);
        getQueue(name);
        rabbitAdmin.deleteQueue(name, ifUnused, ifEmpty);
        log.info("Cola eliminada name={}", name);

        if (withDlq && rabbitAdmin.getQueueInfo(names.dlqOf(name)) != null) {
            rabbitAdmin.deleteQueue(names.dlqOf(name));
            log.info("DLQ eliminada name={}", names.dlqOf(name));
        }
    }

    public int purgeQueue(String name) {
        getQueue(name);
        int purged = rabbitAdmin.purgeQueue(name);
        log.info("Cola vaciada name={} mensajes={}", name, purged);
        return purged;
    }

    // ===================== EXCHANGES =====================

    public void createExchange(ExchangeRequest request) {
        String name = request.getName();
        assertNotReserved(name);
        if (management.exchangeExists(name)) {
            throw new ConflictException("El exchange '" + name + "' ya existe");
        }
        rabbitAdmin.declareExchange(buildExchange(request));
        log.info("Exchange creado name={} type={}", name, request.getType());
    }

    public List<Map<String, Object>> listExchanges() {
        return management.listExchanges();
    }

    public void deleteExchange(String name) {
        assertNotReserved(name);
        if (!management.exchangeExists(name)) {
            throw new NotFoundException("El exchange '" + name + "' no existe");
        }
        rabbitAdmin.deleteExchange(name);
        log.info("Exchange eliminado name={}", name);
    }

    // ===================== BINDINGS =====================

    public void createBinding(BindingRequest request) {
        Binding binding = toBinding(request);
        rabbitAdmin.declareBinding(binding);
        log.info("Binding creado {} -[{}]-> {}", request.getExchange(), key(request), request.getQueue());
    }

    public void deleteBinding(BindingRequest request) {
        Binding binding = toBinding(request);
        rabbitAdmin.removeBinding(binding);
        log.info("Binding eliminado {} -[{}]-> {}", request.getExchange(), key(request), request.getQueue());
    }

    public List<Map<String, Object>> listBindings() {
        return management.listBindings();
    }

    // ===================== MENSAJES DE PRUEBA =====================

    public String publish(PublishRequest request) {
        String id = UUID.randomUUID().toString();
        Message message = MessageBuilder
                .withBody(request.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(id)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        rabbitTemplate.send(request.getExchange(), request.getRoutingKey() == null ? "" : request.getRoutingKey(), message);
        log.info("Mensaje de prueba publicado exchange={} routingKey={} messageId={}",
                request.getExchange(), request.getRoutingKey(), id);
        return id;
    }

    // ===================== helpers =====================

    private Binding toBinding(BindingRequest request) {
        if (rabbitAdmin.getQueueInfo(request.getQueue()) == null) {
            throw new NotFoundException("La cola '" + request.getQueue() + "' no existe");
        }
        if (!management.exchangeExists(request.getExchange())) {
            throw new NotFoundException("El exchange '" + request.getExchange() + "' no existe");
        }
        return new Binding(request.getQueue(), Binding.DestinationType.QUEUE,
                request.getExchange(), key(request), null);
    }

    private String key(BindingRequest request) {
        return request.getRoutingKey() == null ? "" : request.getRoutingKey();
    }

    private void declareDeadLetterInfrastructure(String queueName) {
        rabbitAdmin.declareExchange(new DirectExchange(names.exchanges().dlx(), true, false));
        Queue dlq = new Queue(names.dlqOf(queueName), true);
        rabbitAdmin.declareQueue(dlq);
        rabbitAdmin.declareBinding(new Binding(dlq.getName(), Binding.DestinationType.QUEUE,
                names.exchanges().dlx(), queueName, null));
    }

    private Exchange buildExchange(ExchangeRequest r) {
        return switch (r.getType()) {
            case DIRECT -> new DirectExchange(r.getName(), r.isDurable(), r.isAutoDelete());
            case TOPIC -> new TopicExchange(r.getName(), r.isDurable(), r.isAutoDelete());
            case FANOUT -> new FanoutExchange(r.getName(), r.isDurable(), r.isAutoDelete());
            case HEADERS -> new HeadersExchange(r.getName(), r.isDurable(), r.isAutoDelete());
        };
    }

    /** Protege los objetos internos de RabbitMQ (amq.*) del uso desde la API. */
    private void assertNotReserved(String name) {
        if (name != null && name.startsWith("amq.")) {
            throw new IllegalArgumentException("Los nombres que empiezan con 'amq.' estan reservados por RabbitMQ");
        }
    }
}
