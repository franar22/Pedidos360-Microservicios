package com.pedidos360.catalog.service;

/** Evento de dominio (interno): un producto quedo con stock bajo despues de reservar. */
public record LowStockDetected(Long productId, String productName, int stock, int threshold) {
}
