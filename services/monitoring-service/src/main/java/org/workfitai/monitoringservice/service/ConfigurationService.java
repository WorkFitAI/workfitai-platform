package org.workfitai.monitoringservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigurationService {

    private final VaultTemplate vaultTemplate;
    private final RestTemplate restTemplate;

    private static final Map<String, String> SERVICE_ENDPOINTS = Map.of(
            "user-service",         "http://user-service:9005",
            "auth-service",         "http://auth-service:9005",
            "job-service",          "http://job-service:9005",
            "cv-service",           "http://cv-service:9005",
            "application-service",  "http://application-service:9005"
    );

    public boolean initializeServiceConfig(String serviceName, Map<String, Object> config) {
        try {
            String vaultPath = "secret/data/" + serviceName;

            Map<String, Object> data = new HashMap<>();
            data.put("data", config);

            vaultTemplate.write(vaultPath, data);
            log.info("Successfully initialized config for service: {}", serviceName);
            return true;
        } catch (Exception e) {
            log.error("Failed to initialize config for service: {}", serviceName, e);
            return false;
        }
    }

    public VaultResponse getServiceConfig(String serviceName) {
        try {
            String vaultPath = "secret/data/" + serviceName;
            return vaultTemplate.read(vaultPath);
        } catch (Exception e) {
            log.error("Failed to retrieve config for service: {}", serviceName, e);
            return null;
        }
    }

    public boolean updateServiceConfig(String serviceName, Map<String, Object> config) {
        try {
            String vaultPath = "secret/data/" + serviceName;

            Map<String, Object> data = new HashMap<>();
            data.put("data", config);

            vaultTemplate.write(vaultPath, data);
            log.info("Successfully updated config for service: {}", serviceName);
            return true;
        } catch (Exception e) {
            log.error("Failed to update config for service: {}", serviceName, e);
            return false;
        }
    }

    public boolean refreshService(String serviceName) {
        try {
            String serviceEndpoint = SERVICE_ENDPOINTS.get(serviceName);
            if (serviceEndpoint == null) {
                log.warn("No endpoint configured for service: {}", serviceName);
                return false;
            }

            String refreshUrl = serviceEndpoint + "/actuator/refresh";
            restTemplate.postForEntity(refreshUrl, null, String.class);
            log.info("Successfully refreshed service: {}", serviceName);
            return true;
        } catch (Exception e) {
            log.error("Failed to refresh service: {}", serviceName, e);
            return false;
        }
    }

    public Map<String, Object> getAllServicesStatus() {
        Map<String, Object> status = new HashMap<>();

        for (String serviceName : SERVICE_ENDPOINTS.keySet()) {
            status.put(serviceName, buildServiceStatus(serviceName));
        }

        return status;
    }

    private Map<String, Object> buildServiceStatus(String serviceName) {
        Map<String, Object> serviceStatus = new HashMap<>();

        VaultResponse configResponse = getServiceConfig(serviceName);
        serviceStatus.put("configExists", configResponse != null);

        try {
            String healthUrl = SERVICE_ENDPOINTS.get(serviceName) + "/actuator/health";
            restTemplate.getForEntity(healthUrl, String.class);
            serviceStatus.put("healthy", true);
        } catch (Exception e) {
            serviceStatus.put("healthy", false);
        }

        return serviceStatus;
    }
}
