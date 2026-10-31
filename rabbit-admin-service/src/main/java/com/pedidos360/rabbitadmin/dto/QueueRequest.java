package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Cuerpo de POST /api/rabbit/queues */
@Data
public class QueueRequest {

    @NotBlank(message = "El nombre de la cola es obligatorio")
    @Size(max = 200, message = "El nombre de la cola no puede superar 200 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$",
            message = "El nombre solo puede tener letras, numeros, '.', '_' y '-', y no puede empezar con simbolo")
    private String name;

    private boolean durable = true;

    private boolean autoDelete = false;

    /** Si es true se crea tambien <name>.dlq enlazada al exchange de dead letters. */
    private boolean withDeadLetter = true;

    @Min(value = 1, message = "messageTtlMs debe ser mayor a 0")
    private Long messageTtlMs;

    @Min(value = 1, message = "maxLength debe ser mayor a 0")
    private Integer maxLength;
}
