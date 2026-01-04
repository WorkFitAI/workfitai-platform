package org.workfitai.monitoringservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "monitoring-service");
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    @GetMapping("/")
    public Map<String, Object> root() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "WorkFitAI Monitoring Service");
        response.put("version", "1.0.0");
        response.put("description", "Configuration management and monitoring service");
        response.put("endpoints", new String[] {
                "/health",
                "/api/v1/config/init",
                "/api/v1/config/service/{serviceName}",
                "/api/v1/config/refresh/{serviceName}",
                "/api/v1/config/status"
        });
        return response;
    }
}