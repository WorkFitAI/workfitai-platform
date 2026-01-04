package org.workfitai.monitoringservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.bind.annotation.*;
import org.workfitai.monitoringservice.service.ConfigurationService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigurationController {

    private final ConfigurationService configurationService;

    @Autowired
    public ConfigurationController(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> initializeConfigs(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String serviceName = (String) request.get("serviceName");
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) request.get("config");

            if (serviceName == null || config == null) {
                response.put("success", false);
                response.put("message", "serviceName and config are required");
                return ResponseEntity.badRequest().body(response);
            }

            boolean success = configurationService.initializeServiceConfig(serviceName, config);

            response.put("success", success);
            response.put("message",
                    success ? "Configuration initialized successfully" : "Failed to initialize configuration");
            response.put("serviceName", serviceName);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/service/{serviceName}")
    public ResponseEntity<Map<String, Object>> getServiceConfig(@PathVariable String serviceName) {
        Map<String, Object> response = new HashMap<>();

        try {
            VaultResponse vaultResponse = configurationService.getServiceConfig(serviceName);

            if (vaultResponse != null) {
                response.put("success", true);
                response.put("serviceName", serviceName);
                response.put("config", vaultResponse.getData());
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Configuration not found for service: " + serviceName);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PutMapping("/service/{serviceName}")
    public ResponseEntity<Map<String, Object>> updateServiceConfig(
            @PathVariable String serviceName,
            @RequestBody Map<String, Object> config) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean success = configurationService.updateServiceConfig(serviceName, config);

            response.put("success", success);
            response.put("message", success ? "Configuration updated successfully" : "Failed to update configuration");
            response.put("serviceName", serviceName);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/refresh/{serviceName}")
    public ResponseEntity<Map<String, Object>> refreshService(@PathVariable String serviceName) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean success = configurationService.refreshService(serviceName);

            response.put("success", success);
            response.put("message", success ? "Service refreshed successfully" : "Failed to refresh service");
            response.put("serviceName", serviceName);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAllServicesStatus() {
        try {
            Map<String, Object> status = configurationService.getAllServicesStatus();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("services", status);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}