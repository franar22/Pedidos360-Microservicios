package com.pedidos360.rabbitadmin.controller;

import com.pedidos360.rabbitadmin.dto.ApiResponseDTO;
import com.pedidos360.rabbitadmin.dto.QueueRequest;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Recurso "queues":
 *   POST   /api/rabbit/queues                  crea una cola (y su DLQ)
 *   GET    /api/rabbit/queues                  lista colas (API de gestion)
 *   GET    /api/rabbit/queues/{name}           estado de una cola (mensajes, consumidores)
 *   DELETE /api/rabbit/queues/{name}           elimina la cola (?withDlq=true tambien su DLQ)
 *   DELETE /api/rabbit/queues/{name}/messages  vacia la cola (purge)
 */
@RestController
@RequestMapping("/api/rabbit/queues")
@PreAuthorize("hasRole('ADMIN')")
public class QueueController {

    private final RabbitAdminService adminService;

    public QueueController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    public ResponseEntity<ApiResponseDTO<Map<String, Object>>> create(@Valid @RequestBody QueueRequest request) {
        QueueInformation info = adminService.createQueue(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDTO<>(
                true, HttpStatus.CREATED.value(), "Cola creada satisfactoriamente", toMap(info), 1L));
    }

    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<Map<String, Object>>>> list() {
        List<Map<String, Object>> queues = adminService.listQueues();
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Colas obtenidas satisfactoriamente", queues, (long) queues.size()));
    }

    @GetMapping("/{name}")
    public ResponseEntity<ApiResponseDTO<Map<String, Object>>> get(@PathVariable String name) {
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Cola encontrada", toMap(adminService.getQueue(name)), 1L));
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<ApiResponseDTO<Object>> delete(@PathVariable String name,
                                                         @RequestParam(defaultValue = "false") boolean ifUnused,
                                                         @RequestParam(defaultValue = "false") boolean ifEmpty,
                                                         @RequestParam(defaultValue = "false") boolean withDlq) {
        adminService.deleteQueue(name, ifUnused, ifEmpty, withDlq);
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Cola eliminada satisfactoriamente", null, 1L));
    }

    @DeleteMapping("/{name}/messages")
    public ResponseEntity<ApiResponseDTO<Map<String, Object>>> purge(@PathVariable String name) {
        int purged = adminService.purgeQueue(name);
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Cola vaciada satisfactoriamente", Map.of("purged", purged), 1L));
    }

    private Map<String, Object> toMap(QueueInformation info) {
        return Map.of("name", info.getName(),
                "messageCount", info.getMessageCount(),
                "consumerCount", info.getConsumerCount());
    }
}
