package com.pedidos360.catalog.repository;

import com.pedidos360.catalog.model.StockAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

    boolean existsByEventId(String eventId);

    List<StockAlert> findTop50ByOrderByCreatedAtDesc();
}
