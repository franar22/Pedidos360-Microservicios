package com.pedidos360.rabbitadmin.controller;

import com.pedidos360.rabbitadmin.dto.ApiResponseDTO;
import com.pedidos360.rabbitadmin.dto.ExchangeRequest;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Recurso "exchanges":
 *   POST   /api/rabbit/exchanges          crea un exchange (DIRECT | TOPIC | FANOUT | HEADERS)
 *   GET    /api/rabbit/exchanges          lista exchanges
 *   DELETE /api/rabbit/exchanges/{name}   elimina un exchange
 */
@RestController
@RequestMapping("/api/rabbit/exchanges")
@PreAuthorize("hasRole('ADMIN')")
public class ExchangeController {

    private final RabbitAdminService adminService;

    public ExchangeController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    public ResponseEntity<ApiResponseDTO<ExchangeRequest>> create(@Valid @RequestBody ExchangeRequest request) {
        adminService.createExchange(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDTO<>(
                true, HttpStatus.CREATED.value(), "Exchange creado satisfactoriamente", request, 1L));
    }

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<Map<String, Object>>>> list() {
        List<Map<String, Object>> exchanges = adminService.listExchanges();
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Exchanges obtenidos satisfactoriamente", exchanges, (long) exchanges.size()));
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<ApiResponseDTO<Object>> delete(@PathVariable String name) {
        adminService.deleteExchange(name);
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Exchange eliminado satisfactoriamente", null, 1L));
    }
}
