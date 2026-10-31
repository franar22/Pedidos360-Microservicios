package com.pedidos360.catalog.messaging.support;

import org.springframework.amqp.core.Message;

@FunctionalInterface
public interface MessageHandler {

    void handle(Message message) throws Exception;
}
