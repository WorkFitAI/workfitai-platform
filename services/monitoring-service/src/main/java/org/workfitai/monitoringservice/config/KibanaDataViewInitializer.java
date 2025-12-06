package org.workfitai.monitoringservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Initializes Kibana Data View for WorkFitAI logs.
 * Creates a pre-configured data view so users don't have to set it up manually.
 */
@Component
@Slf4j
public class KibanaDataViewInitializer {

    @Value("${kibana.host:kibana}")
    private String kibanaHost;

    @Value("${kibana.port:5601}")
    private int kibanaPort;

    @Value("${kibana.scheme:http}")
    private String kibanaScheme;

    @Value("${kibana.username:elastic}")
    private String kibanaUsername;

    @Value("${kibana.password:}")
    private String kibanaPassword;

    @Value("${elasticsearch.index-prefix:workfitai-logs}")
    private String indexPrefix;

    private final RestTemplate restTemplate = new RestTemplate();

    @EventListener(ApplicationReadyEvent.class)
    @Order(100) // Run after ElasticsearchIndexInitializer
    public void initializeKibanaDataView() {
        try {
            log.info("🔧 Initializing Kibana Data View for pattern: {}-*", indexPrefix);

            // Wait a bit for Kibana to be fully ready
            Thread.sleep(5000);

            String kibanaUrl = String.format("%s://%s:%d", kibanaScheme, kibanaHost, kibanaPort);

            // Check if Kibana is available
            if (!isKibanaAvailable(kibanaUrl)) {
                log.warn("⚠️ Kibana is not available at {}. Skipping Data View creation.", kibanaUrl);
                return;
            }

            // Check if data view already exists
            if (dataViewExists(kibanaUrl)) {
                log.info("✅ Kibana Data View 'workfitai-logs' already exists");
                return;
            }

            // Create data view
            createDataView(kibanaUrl);

        } catch (Exception e) {
            log.warn("⚠️ Failed to initialize Kibana Data View: {}", e.getMessage());
            // Don't fail startup - users can create it manually
        }
    }

    private boolean isKibanaAvailable(String kibanaUrl) {
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    kibanaUrl + "/api/status",
                    HttpMethod.GET,
                    entity,
                    String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean dataViewExists(String kibanaUrl) {
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    kibanaUrl + "/api/data_views/data_view/workfitai-logs",
                    HttpMethod.GET,
                    entity,
                    String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private void createDataView(String kibanaUrl) {
        try {
            HttpHeaders headers = createHeaders();

            String requestBody = """
                    {
                        "data_view": {
                            "id": "workfitai-logs",
                            "title": "%s-*",
                            "name": "WorkFitAI Logs",
                            "timeFieldName": "@timestamp",
                            "allowNoIndex": true
                        }
                    }
                    """.formatted(indexPrefix);

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    kibanaUrl + "/api/data_views/data_view",
                    HttpMethod.POST,
                    entity,
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Successfully created Kibana Data View 'WorkFitAI Logs'");

                // Set as default data view
                setAsDefaultDataView(kibanaUrl);
            } else {
                log.warn("⚠️ Failed to create Kibana Data View: {}", response.getBody());
            }

        } catch (Exception e) {
            log.warn("⚠️ Error creating Kibana Data View: {}", e.getMessage());
        }
    }

    private void setAsDefaultDataView(String kibanaUrl) {
        try {
            HttpHeaders headers = createHeaders();

            String requestBody = """
                    {
                        "data_view_id": "workfitai-logs",
                        "force": true
                    }
                    """;

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            restTemplate.exchange(
                    kibanaUrl + "/api/data_views/default",
                    HttpMethod.POST,
                    entity,
                    String.class);

            log.info("✅ Set 'WorkFitAI Logs' as default Data View");

        } catch (Exception e) {
            log.debug("Could not set default data view: {}", e.getMessage());
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("kbn-xsrf", "true");

        // Add Basic Authentication if password is provided
        if (kibanaPassword != null && !kibanaPassword.isBlank()) {
            String auth = kibanaUsername + ":" + kibanaPassword;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encodedAuth);
        }

        return headers;
    }
}
