package com.pedidos360.order.repository;

import com.pedidos360.order.model.OrderAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderAuditLogRepository extends JpaRepository<OrderAuditLog, Long> {

    boolean existsByEventId(String eventId);

    List<OrderAuditLog> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
