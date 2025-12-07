package org.workfitai.monitoringservice.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DateProperty;
import co.elastic.clients.elasticsearch._types.mapping.KeywordProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Initializes Elasticsearch index template for WorkFitAI logs.
 * Creates optimized mappings for log search and aggregation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchIndexInitializer {

    private final ElasticsearchClient elasticsearchClient;

    @Value("${elasticsearch.index-prefix:workfitai-logs}")
    private String indexPrefix;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndexTemplate() {
        try {
            log.info("🔧 Initializing Elasticsearch index template for pattern: {}-*", indexPrefix);

            // Check if template already exists
            boolean templateExists = elasticsearchClient.indices()
                    .existsIndexTemplate(e -> e.name("workfitai-logs-template"))
                    .value();

            if (templateExists) {
                log.info("✅ Index template 'workfitai-logs-template' already exists");
                return;
            }

            // Create index template with optimized mappings
            elasticsearchClient.indices().putIndexTemplate(PutIndexTemplateRequest.of(t -> t
                    .name("workfitai-logs-template")
                    .indexPatterns(List.of(indexPrefix + "-*"))
                    .priority(100)
                    .template(template -> template
                            .settings(s -> s
                                    .numberOfShards("1")
                                    .numberOfReplicas("0")
                                    .index(i -> i
                                            .refreshInterval(ri -> ri.time("5s"))))
                            .mappings(m -> m
                                    .properties(buildMappings())
                                    .dynamic(co.elastic.clients.elasticsearch._types.mapping.DynamicMapping.True)))));

            log.info("✅ Successfully created index template 'workfitai-logs-template'");

        } catch (Exception e) {
            log.warn("⚠️ Failed to initialize Elasticsearch index template: {}", e.getMessage());
            // Don't fail startup - Fluent Bit will create indices with default mappings
        }
    }

    private Map<String, Property> buildMappings() {
        return Map.ofEntries(
                // Timestamp field (required for Kibana Data View)
                Map.entry("@timestamp", Property.of(p -> p
                        .date(DateProperty.of(d -> d)))),

                // Service identification
                Map.entry("service", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))),
                Map.entry("environment", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))),

                // Log level
                Map.entry("level", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))),

                // Request correlation
                Map.entry("requestId", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))),
                Map.entry("traceId", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))),
                Map.entry("spanId", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))),

                // User context
                Map.entry("username", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))),
                Map.entry("userId", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))),
                Map.entry("roles", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))),

                // HTTP context
                Map.entry("path", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))),
                Map.entry("method", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))),

                // Logger info
                Map.entry("logger_name", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))),
                Map.entry("thread_name", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))),

                // Log message (full-text searchable)
                Map.entry("message", Property.of(p -> p
                        .text(TextProperty.of(t -> t
                                .analyzer("standard")
                                .fields("keyword", f -> f.keyword(KeywordProperty.of(k -> k
                                        .ignoreAbove(256)))))))),

                // Stack trace
                Map.entry("stack_trace", Property.of(p -> p
                        .text(TextProperty.of(t -> t
                                .analyzer("standard"))))),

                // Container info (from Fluent Bit)
                Map.entry("container_id", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))),
                Map.entry("container_name", Property.of(p -> p
                        .keyword(KeywordProperty.of(k -> k)))));
    }
}
