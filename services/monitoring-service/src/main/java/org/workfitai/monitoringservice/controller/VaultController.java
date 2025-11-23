package org.workfitai.monitoringservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workfitai.monitoringservice.config.VaultInitializer;
import org.workfitai.monitoringservice.service.ConfigurationService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/vault")
public class VaultController {

    private static final Logger logger = LoggerFactory.getLogger(VaultController.class);

    private final ConfigurationService configurationService;
    private final VaultInitializer vaultInitializer;

    @Autowired
    public VaultController(ConfigurationService configurationService, VaultInitializer vaultInitializer) {
        this.configurationService = configurationService;
        this.vaultInitializer = vaultInitializer;
    }

    @PostMapping("/reinitialize")
    public ResponseEntity<Map<String, Object>> reinitializeSecrets() {
        Map<String, Object> response = new HashMap<>();

        try {
            vaultInitializer.run();
            response.put("status", "success");
            response.put("message", "Vault secrets reinitialized successfully");
            logger.info("Vault secrets manually reinitialized via API");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to reinitialize vault secrets: " + e.getMessage());
            logger.error("Failed to manually reinitialize vault secrets", e);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getVaultStatus() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> servicesStatus = new HashMap<>();

        String[] services = { "auth-service", "user-service", "api-gateway", "job-service",
                "cv-service", "application-service", "monitoring-service" };

        for (String service : services) {
            try {
                var config = configurationService.getServiceConfig(service);
                servicesStatus.put(service, config != null ? "configured" : "missing");
            } catch (Exception e) {
                servicesStatus.put(service, "error: " + e.getMessage());
            }
        }

        response.put("services", servicesStatus);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/config/{serviceName}")
    public ResponseEntity<Map<String, Object>> getServiceConfig(@PathVariable String serviceName) {
        try {
            var config = configurationService.getServiceConfig(serviceName);
            Map<String, Object> response = new HashMap<>();

            if (config != null) {
                response.put("status", "found");
                response.put("data", config.getData());
            } else {
                response.put("status", "not_found");
                response.put("message", "No configuration found for service: " + serviceName);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}