package com.pedidos360.order.messaging.support;

/**
 * Error que NO se arregla reintentando (JSON corrupto, campos obligatorios faltantes, etc.).
 * El consumidor hace NACK sin reencolar y el mensaje va directo a la DLQ.
 */
public class NonRecoverableMessageException extends RuntimeException {

    public NonRecoverableMessageException(String message) {
        super(message);
    }

    public NonRecoverableMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
