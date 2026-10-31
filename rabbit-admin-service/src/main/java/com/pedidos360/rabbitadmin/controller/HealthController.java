package com.pedidos360.rabbitadmin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rabbit")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "statusCode", 200, "service", "rabbit-admin-service",
                "message", "rabbit-admin-service funcionando correctamente");
    }
}
