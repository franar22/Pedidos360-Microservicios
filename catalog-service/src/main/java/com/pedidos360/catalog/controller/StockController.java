package com.pedidos360.catalog.controller;

import com.pedidos360.catalog.dto.ApiResponseDTO;
import com.pedidos360.catalog.dto.StockAdjustRequestDTO;
import com.pedidos360.catalog.model.Product;
import com.pedidos360.catalog.service.StockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints que consume order-service (reenviando el JWT del usuario que acepta/cancela el pedido).
 * Solo ADMIN y OPERADOR, igual que la regla del monolito para cambiar estados.
 */
@RestController
@RequestMapping("/api/catalog/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/reserve")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<ApiResponseDTO<List<Product>>> reserve(@Valid @RequestBody StockAdjustRequestDTO dto) {
        List<Product> products = stockService.reserve(dto.getItems());
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Stock reservado satisfactoriamente", products, (long) products.size()));
    }

    @PostMapping("/release")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<ApiResponseDTO<List<Product>>> release(@Valid @RequestBody StockAdjustRequestDTO dto) {
        List<Product> products = stockService.release(dto.getItems());
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Stock devuelto satisfactoriamente", products, (long) products.size()));
    }
}
