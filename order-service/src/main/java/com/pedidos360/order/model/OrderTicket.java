package com.pedidos360.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Ticket/comprobante generado de forma asincrona (cola tickets) cuando el pedido es ACEPTADO. */
@Entity
@Table(name = "order_ticket")
@Getter
@Setter
@NoArgsConstructor
public class OrderTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Llave del objeto en S3 si hay bucket configurado (patron claim check). */
    @Column(name = "s3_key", length = 255)
    private String s3Key;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
