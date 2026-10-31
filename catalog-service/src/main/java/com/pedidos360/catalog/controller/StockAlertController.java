package com.pedidos360.catalog.controller;

import com.pedidos360.catalog.dto.ApiResponseDTO;
import com.pedidos360.catalog.model.StockAlert;
import com.pedidos360.catalog.repository.StockAlertRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Alertas de stock bajo generadas por el consumidor RabbitMQ (util para mostrar el flujo asincrono). */
@RestController
@RequestMapping("/api/catalog/alerts")
public class StockAlertController {

    private final StockAlertRepository alertRepository;

    public StockAlertController(StockAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<ApiResponseDTO<List<StockAlert>>> latest() {
        List<StockAlert> alerts = alertRepository.findTop50ByOrderByCreatedAtDesc();
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Alertas de stock bajo", alerts, (long) alerts.size()));
    }
}
