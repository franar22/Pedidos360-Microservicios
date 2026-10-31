package com.pedidos360.order.dto;

import com.pedidos360.order.model.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusUpdateDTO {

    @NotNull(message = "status es obligatorio")
    private OrderStatus status;
}
