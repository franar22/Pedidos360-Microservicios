package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Cuerpo de POST /api/rabbit/messages: publica un mensaje de prueba (ej. JSON invalido para provocar la DLQ). */
@Data
public class PublishRequest {

    @NotBlank(message = "El exchange es obligatorio")
    private String exchange;

    @Size(max = 255, message = "La routing key no puede superar 255 caracteres")
    private String routingKey = "";

    @NotBlank(message = "El payload es obligatorio")
    @Size(max = 100000, message = "El payload no puede superar 100000 caracteres")
    private String payload;
}
