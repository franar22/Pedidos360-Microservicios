package com.pedidos360.order.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pedidos360.order.model.Order;
import com.pedidos360.order.model.OrderItem;
import com.pedidos360.order.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evento de dominio Y contrato del mensaje que viaja por SNS/SQS y RabbitMQ.
 * Describe algo que YA ocurrio (verbo en pasado), con id unico y version.
 * Se envian IDs y datos minimos (no entidades completas).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderEvent(String eventId,
                         String type,
                         int version,
                         Instant occurredAt,
                         Long orderId,
                         String customerId,
                         String customerEmail,
                         String previousStatus,
                         String status,
                         BigDecimal total,
                         List<Item> items) {

    public static final String TYPE_CREATED = "PedidoCreado";
    public static final String TYPE_STATUS_CHANGED = "PedidoEstadoCambiado";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Long productId, String productName, Integer quantity, BigDecimal unitPrice) {}

    public static OrderEvent created(Order order) {
        return build(TYPE_CREATED, order, null);
    }

    public static OrderEvent statusChanged(Order order, OrderStatus previous) {
        return build(TYPE_STATUS_CHANGED, order, previous);
    }

    private static OrderEvent build(String type, Order order, OrderStatus previous) {
        List<Item> items = order.getItems().stream()
                .map((OrderItem i) -> new Item(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderEvent(
                UUID.randomUUID().toString(), type, 1, Instant.now(),
                order.getId(), order.getCustomerId(), order.getCustomerEmail(),
                previous == null ? null : previous.name(), order.getStatus().name(),
                order.getTotal(), items);
    }
}
