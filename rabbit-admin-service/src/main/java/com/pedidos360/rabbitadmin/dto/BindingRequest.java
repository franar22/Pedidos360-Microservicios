package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Cuerpo de POST /api/rabbit/bindings (exchange -> cola con routing key). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BindingRequest {

    @NotBlank(message = "El exchange es obligatorio")
    private String exchange;

    @NotBlank(message = "La cola es obligatoria")
    private String queue;

    /** Puede ir vacia para exchanges fanout. */
    @Size(max = 255, message = "La routing key no puede superar 255 caracteres")
    private String routingKey = "";
}
