package com.pedidos360.order.controller;

import com.pedidos360.order.dto.ApiResponseDTO;
import com.pedidos360.order.dto.OrderRequestDTO;
import com.pedidos360.order.dto.StatusUpdateDTO;
import com.pedidos360.order.model.Order;
import com.pedidos360.order.model.OrderAuditLog;
import com.pedidos360.order.model.OrderTicket;
import com.pedidos360.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pedidos (mismas rutas y reglas que el monolito):
 *  - Crear pedido: CLIENTE, OPERADOR, ADMIN (cada uno crea "el suyo")
 *  - Ver todos los pedidos: ADMIN, OPERADOR (CLIENTE solo ve los propios)
 *  - Cambiar estado: ADMIN, OPERADOR
 * Nuevos (evidencia del flujo asincrono): GET /{id}/ticket (cola RabbitMQ) y GET /{id}/audit (cola SQS).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR', 'CLIENTE')")
    public ResponseEntity<ApiResponseDTO<List<Order>>> list(@AuthenticationPrincipal Jwt jwt) {
        List<Order> orders;

        if (isStaff(jwt)) {
            orders = orderService.findAll();
        } else {
            orders = orderService.findByCustomer(jwt.getSubject());
        }

        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Pedidos obtenidos satisfactoriamente",
                orders, (long) orders.size()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR', 'CLIENTE')")
    public ResponseEntity<ApiResponseDTO<Order>> getById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Order order = orderService.findById(id);
        assertOwnerOrStaff(order, jwt);
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Pedido encontrado", order, 1L));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR', 'CLIENTE')")
    public ResponseEntity<ApiResponseDTO<Order>> create(@Valid @RequestBody OrderRequestDTO dto,
                                                        @AuthenticationPrincipal Jwt jwt) {
        String customerId = jwt.getSubject();
        String customerEmail = jwt.getClaimAsString("email");

        Order order = orderService.create(dto, customerId, customerEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDTO<>(
                true, HttpStatus.CREATED.value(), "Pedido creado satisfactoriamente", order, 1L));
    }

    // Cambio de estado: solo ADMIN u OPERADOR. La maquina de estados (OrderStateMachine) garantiza
    // que no se pueda DESPACHAR sin haber ACEPTADO antes.
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<ApiResponseDTO<Order>> changeStatus(@PathVariable Long id,
                                                              @Valid @RequestBody StatusUpdateDTO dto) {
        Order order = orderService.changeStatus(id, dto.getStatus());
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Estado del pedido actualizado satisfactoriamente", order, 1L));
    }

    @GetMapping("/{id}/ticket")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR', 'CLIENTE')")
    public ResponseEntity<ApiResponseDTO<OrderTicket>> ticket(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        assertOwnerOrStaff(orderService.findById(id), jwt);
        OrderTicket ticket = orderService.findTicket(id);
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Ticket del pedido", ticket, 1L));
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<ApiResponseDTO<List<OrderAuditLog>>> audit(@PathVariable Long id) {
        List<OrderAuditLog> audit = orderService.findAudit(id);
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Bitacora del pedido", audit, (long) audit.size()));
    }

    private boolean hasRole(Jwt jwt, String role) {
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        return groups != null && groups.stream().anyMatch(g -> g.equalsIgnoreCase(role));
    }

    private boolean isStaff(Jwt jwt) {
        return hasRole(jwt, "ADMIN") || hasRole(jwt, "OPERADOR");
    }

    private void assertOwnerOrStaff(Order order, Jwt jwt) {
        boolean isOwner = order.getCustomerId().equals(jwt.getSubject());
        if (!isStaff(jwt) && !isOwner) {
            throw new AccessDeniedException("No puedes ver pedidos de otro cliente.");
        }
    }
}
