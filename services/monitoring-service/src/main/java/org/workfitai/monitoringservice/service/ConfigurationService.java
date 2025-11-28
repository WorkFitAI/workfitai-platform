package org.workfitai.monitoringservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class ConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);

    private final VaultTemplate vaultTemplate;
    private final RestTemplate restTemplate;

    private static final Map<String, String> SERVICE_ENDPOINTS = new HashMap<String, String>() {
        {
            put("user-service", "http://user-service:9005");
            put("auth-service", "http://auth-service:9005");
            put("job-service", "http://job-service:9005");
            put("cv-service", "http://cv-service:9005");
            put("application-service", "http://application-service:9005");
        }
    };

    @Autowired
    public ConfigurationService(VaultTemplate vaultTemplate, RestTemplate restTemplate) {
        this.vaultTemplate = vaultTemplate;
        this.restTemplate = restTemplate;
    }

    public boolean initializeServiceConfig(String serviceName, Map<String, Object> config) {
        try {
            String vaultPath = "secret/data/" + serviceName;

            // Wrap config data for KV v2
            Map<String, Object> data = new HashMap<>();
            data.put("data", config);

            vaultTemplate.write(vaultPath, data);
            logger.info("Successfully initialized config for service: {}", serviceName);
            return true;
        } catch (Exception e) {
            logger.error("Failed to initialize config for service: {}", serviceName, e);
            return false;
        }
    }

    public VaultResponse getServiceConfig(String serviceName) {
        try {
            String vaultPath = "secret/data/" + serviceName;
            return vaultTemplate.read(vaultPath);
        } catch (Exception e) {
            logger.error("Failed to retrieve config for service: {}", serviceName, e);
            return null;
        }
    }

    public boolean updateServiceConfig(String serviceName, Map<String, Object> config) {
        try {
            String vaultPath = "secret/data/" + serviceName;

            // Wrap config data for KV v2
            Map<String, Object> data = new HashMap<>();
            data.put("data", config);

            vaultTemplate.write(vaultPath, data);
            logger.info("Successfully updated config for service: {}", serviceName);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update config for service: {}", serviceName, e);
            return false;
        }
    }

    public boolean refreshService(String serviceName) {
        try {
            String serviceEndpoint = SERVICE_ENDPOINTS.get(serviceName);
            if (serviceEndpoint == null) {
                logger.warn("No endpoint configured for service: {}", serviceName);
                return false;
            }

            String refreshUrl = serviceEndpoint + "/actuator/refresh";
            restTemplate.postForEntity(refreshUrl, null, String.class);
            logger.info("Successfully refreshed service: {}", serviceName);
            return true;
        } catch (Exception e) {
            logger.error("Failed to refresh service: {}", serviceName, e);
            return false;
        }
    }

    public Map<String, Object> getAllServicesStatus() {
        Map<String, Object> status = new HashMap<>();

        for (String serviceName : SERVICE_ENDPOINTS.keySet()) {
            Map<String, Object> serviceStatus = new HashMap<>();

            // Check if config exists in Vault
            VaultResponse configResponse = getServiceConfig(serviceName);
            serviceStatus.put("configExists", configResponse != null);

            // Check if service is responsive
            try {
                String serviceEndpoint = SERVICE_ENDPOINTS.get(serviceName);
                String healthUrl = serviceEndpoint + "/actuator/health";
                restTemplate.getForEntity(healthUrl, String.class);
                serviceStatus.put("healthy", true);
            } catch (Exception e) {
                serviceStatus.put("healthy", false);
            }

            status.put(serviceName, serviceStatus);
        }

        return status;
    }
}