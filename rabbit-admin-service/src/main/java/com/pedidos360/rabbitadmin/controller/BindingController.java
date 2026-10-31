package com.pedidos360.rabbitadmin.controller;

import com.pedidos360.rabbitadmin.dto.ApiResponseDTO;
import com.pedidos360.rabbitadmin.dto.BindingRequest;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Recurso "bindings" (exchange -> cola con routing key):
 *   POST   /api/rabbit/bindings                                   crea el binding
 *   GET    /api/rabbit/bindings                                   lista bindings
 *   DELETE /api/rabbit/bindings?exchange=..&queue=..&routingKey=.. elimina el binding
 */
@RestController
@RequestMapping("/api/rabbit/bindings")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class BindingController {

    private final RabbitAdminService adminService;

    public BindingController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    public ResponseEntity<ApiResponseDTO<BindingRequest>> create(@Valid @RequestBody BindingRequest request) {
        adminService.createBinding(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDTO<>(
                true, HttpStatus.CREATED.value(), "Binding creado satisfactoriamente", request, 1L));
    }

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<Map<String, Object>>>> list() {
        List<Map<String, Object>> bindings = adminService.listBindings();
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Bindings obtenidos satisfactoriamente", bindings, (long) bindings.size()));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponseDTO<Object>> delete(
            @RequestParam @NotBlank(message = "El exchange es obligatorio") String exchange,
            @RequestParam @NotBlank(message = "La cola es obligatoria") String queue,
            @RequestParam(defaultValue = "") String routingKey) {
        adminService.deleteBinding(new BindingRequest(exchange, queue, routingKey));
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Binding eliminado satisfactoriamente", null, 1L));
    }
}
