package com.pedidos360.rabbitadmin.controller;

import com.pedidos360.rabbitadmin.dto.ApiResponseDTO;
import com.pedidos360.rabbitadmin.dto.PublishRequest;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** POST /api/rabbit/messages : publica un mensaje de prueba (p. ej. JSON corrupto para demostrar la DLQ). */
@RestController
@RequestMapping("/api/rabbit/messages")
@PreAuthorize("hasRole('ADMIN')")
public class MessageController {

    private final RabbitAdminService adminService;

    public MessageController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    public ResponseEntity<ApiResponseDTO<Map<String, Object>>> publish(@Valid @RequestBody PublishRequest request) {
        String id = adminService.publish(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ApiResponseDTO<>(
                true, HttpStatus.ACCEPTED.value(), "Mensaje publicado", Map.of("messageId", id), 1L));
    }
}
