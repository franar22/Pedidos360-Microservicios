package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Cuerpo de POST /api/rabbit/exchanges */
@Data
public class ExchangeRequest {

    @NotBlank(message = "El nombre del exchange es obligatorio")
    @Size(max = 200, message = "El nombre del exchange no puede superar 200 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$",
            message = "El nombre solo puede tener letras, numeros, '.', '_' y '-', y no puede empezar con simbolo")
    private String name;

    @NotNull(message = "El tipo de exchange es obligatorio (DIRECT, TOPIC, FANOUT o HEADERS)")
    private ExchangeType type;

    private boolean durable = true;

    private boolean autoDelete = false;
}
