package com.pedidos360.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class OrderRequestDTO {

    @NotEmpty(message = "El pedido debe tener al menos un item")
    @Valid
    private List<OrderItemRequestDTO> items;
}
