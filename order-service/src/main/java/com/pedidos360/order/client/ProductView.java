package com.pedidos360.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Vista minima del producto tal como lo expone catalog-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductView(Long id, String name, String description, BigDecimal price, Integer stock) {
}
