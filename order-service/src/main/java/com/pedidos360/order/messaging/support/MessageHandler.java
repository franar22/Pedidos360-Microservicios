package com.pedidos360.order.messaging.support;

import org.springframework.amqp.core.Message;

@FunctionalInterface
public interface MessageHandler {

    void handle(Message message) throws Exception;
}
