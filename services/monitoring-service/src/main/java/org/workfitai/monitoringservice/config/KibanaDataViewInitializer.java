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

            // Delete existing data view to recreate with new format
            if (dataViewExists(kibanaUrl)) {
                log.info("🔄 Deleting existing Data View to apply new formatters...");
                deleteDataView(kibanaUrl);
            }

            // Create data view with color formatters
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

    private void deleteDataView(String kibanaUrl) {
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            restTemplate.exchange(
                    kibanaUrl + "/api/data_views/data_view/workfitai-logs",
                    HttpMethod.DELETE,
                    entity,
                    String.class);
            log.info("🗑️ Deleted existing Data View 'workfitai-logs'");

            // Also delete saved search if exists
            try {
                restTemplate.exchange(
                        kibanaUrl + "/api/saved_objects/search/workfitai-logs-search",
                        HttpMethod.DELETE,
                        entity,
                        String.class);
            } catch (Exception ignored) {
            }

        } catch (Exception e) {
            log.debug("Could not delete data view: {}", e.getMessage());
        }
    }

    private void createDataView(String kibanaUrl) {
        try {
            HttpHeaders headers = createHeaders();

            // Create Data View with field formatters for colored display
            // Using field names that match Fluent Bit output
            String fieldFormatMap = """
                    {
                        "log_level": {
                            "id": "color",
                            "params": {
                                "fieldType": "string",
                                "colors": [
                                    {"range": "-Infinity:Infinity", "regex": "ERROR", "text": "#ffffff", "background": "#bd271e"},
                                    {"range": "-Infinity:Infinity", "regex": "WARN", "text": "#000000", "background": "#f5a700"},
                                    {"range": "-Infinity:Infinity", "regex": "INFO", "text": "#ffffff", "background": "#006bb4"},
                                    {"range": "-Infinity:Infinity", "regex": "DEBUG", "text": "#ffffff", "background": "#017d73"},
                                    {"range": "-Infinity:Infinity", "regex": "TRACE", "text": "#ffffff", "background": "#69707d"}
                                ]
                            }
                        },
                        "service_name": {
                            "id": "color",
                            "params": {
                                "fieldType": "string",
                                "colors": [
                                    {"range": "-Infinity:Infinity", "regex": "auth-service", "text": "#ffffff", "background": "#6092c0"},
                                    {"range": "-Infinity:Infinity", "regex": "user-service", "text": "#ffffff", "background": "#d36086"},
                                    {"range": "-Infinity:Infinity", "regex": "job-service", "text": "#ffffff", "background": "#9170b8"},
                                    {"range": "-Infinity:Infinity", "regex": "notification-service", "text": "#ffffff", "background": "#ca8eae"},
                                    {"range": "-Infinity:Infinity", "regex": "cv-service", "text": "#ffffff", "background": "#d6bf57"},
                                    {"range": "-Infinity:Infinity", "regex": "application-service", "text": "#ffffff", "background": "#54b399"},
                                    {"range": "-Infinity:Infinity", "regex": "api-gateway", "text": "#ffffff", "background": "#b9a888"},
                                    {"range": "-Infinity:Infinity", "regex": "monitoring-service", "text": "#ffffff", "background": "#da8b45"}
                                ]
                            }
                        },
                        "http_method": {
                            "id": "color",
                            "params": {
                                "fieldType": "string",
                                "colors": [
                                    {"range": "-Infinity:Infinity", "regex": "GET", "text": "#ffffff", "background": "#006bb4"},
                                    {"range": "-Infinity:Infinity", "regex": "POST", "text": "#ffffff", "background": "#017d73"},
                                    {"range": "-Infinity:Infinity", "regex": "PUT", "text": "#000000", "background": "#f5a700"},
                                    {"range": "-Infinity:Infinity", "regex": "DELETE", "text": "#ffffff", "background": "#bd271e"},
                                    {"range": "-Infinity:Infinity", "regex": "PATCH", "text": "#ffffff", "background": "#9170b8"}
                                ]
                            }
                        }
                    }
                    """
                    .replace("\n", "").replace("    ", "");

            String requestBody = """
                    {
                        "data_view": {
                            "id": "workfitai-logs",
                            "title": "%s-*",
                            "name": "WorkFitAI Logs",
                            "timeFieldName": "@timestamp",
                            "allowNoIndex": true,
                            "fieldFormats": %s
                        }
                    }
                    """.formatted(indexPrefix, fieldFormatMap);

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    kibanaUrl + "/api/data_views/data_view",
                    HttpMethod.POST,
                    entity,
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Successfully created Kibana Data View 'WorkFitAI Logs' with color formatters");

                // Set as default data view
                setAsDefaultDataView(kibanaUrl);

                // Create saved search with predefined columns
                createSavedSearch(kibanaUrl);
            } else {
                log.warn("⚠️ Failed to create Kibana Data View: {}", response.getBody());
            }

        } catch (Exception e) {
            log.warn("⚠️ Error creating Kibana Data View: {}", e.getMessage());
        }
    }

    private void createSavedSearch(String kibanaUrl) {
        try {
            HttpHeaders headers = createHeaders();

            // Use correct field names matching Fluent Bit output:
            // service_name, log_level, log_message, logger, request_id, user, http_method,
            // http_path
            String requestBody = """
                    {
                        "attributes": {
                            "title": "WorkFitAI Logs - Formatted",
                            "description": "Pre-configured log view with colored fields and organized columns",
                            "columns": ["service_name", "log_level", "log_message", "logger_class", "request_id", "user", "http_method", "http_path"],
                            "sort": [["@timestamp", "desc"]],
                            "kibanaSavedObjectMeta": {
                                "searchSourceJSON": "{\\"query\\":{\\"query\\":\\"\\",\\"language\\":\\"kuery\\"},\\"filter\\":[],\\"indexRefName\\":\\"kibanaSavedObjectMeta.searchSourceJSON.index\\"}"
                            }
                        },
                        "references": [
                            {
                                "id": "workfitai-logs",
                                "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
                                "type": "index-pattern"
                            }
                        ]
                    }
                    """;

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    kibanaUrl + "/api/saved_objects/search/workfitai-logs-search",
                    HttpMethod.POST,
                    entity,
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Successfully created Kibana Saved Search 'WorkFitAI Logs - Formatted'");
            } else {
                log.debug("Saved Search creation response: {}", response.getBody());
            }

        } catch (Exception e) {
            log.debug("Could not create saved search: {}", e.getMessage());
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
