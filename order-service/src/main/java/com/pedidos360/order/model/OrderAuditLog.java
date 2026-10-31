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

/**
 * Bitacora de eventos de pedidos, escrita por el consumidor SQS (AuditoriaSqsListener).
 * event_id UNIQUE = idempotencia (SQS Standard puede entregar un mensaje mas de una vez).
 */
@Entity
@Table(name = "order_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class OrderAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(length = 20)
    private String previousStatus;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
