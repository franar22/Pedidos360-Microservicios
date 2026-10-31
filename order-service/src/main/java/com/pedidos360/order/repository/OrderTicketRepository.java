package com.pedidos360.order.repository;

import com.pedidos360.order.model.OrderTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderTicketRepository extends JpaRepository<OrderTicket, Long> {

    boolean existsByOrderId(Long orderId);

    Optional<OrderTicket> findByOrderId(Long orderId);
}
