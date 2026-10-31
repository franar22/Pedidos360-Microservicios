package com.pedidos360.rabbitadmin.exception;

/** RabbitMQ (AMQP o API de gestion) no responde. Se traduce a HTTP 503. */
public class BrokerUnavailableException extends RuntimeException {
    public BrokerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
