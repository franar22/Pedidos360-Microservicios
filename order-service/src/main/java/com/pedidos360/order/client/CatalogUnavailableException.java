package com.pedidos360.order.client;

/** catalog-service no responde o respondio un error de servidor. Se traduce a HTTP 503. */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
