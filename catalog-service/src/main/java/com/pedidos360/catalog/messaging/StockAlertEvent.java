package com.pedidos360.catalog.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/** Contrato del mensaje "StockBajo" (verbo en pasado, id unico, version). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockAlertEvent(String eventId,
                              String type,
                              int version,
                              Instant occurredAt,
                              Long productId,
                              String productName,
                              int stock,
                              int threshold) {

    public static final String TYPE = "StockBajo";
}
