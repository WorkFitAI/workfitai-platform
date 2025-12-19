package org.workfitai.monitoringservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.workfitai.monitoringservice.dto.*;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for admin-facing user activity queries.
 * Focuses on meaningful user actions, not system logs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminActivityService {

    private final ElasticsearchClient elasticsearchClient;

    @Value("${elasticsearch.index-pattern:workfitai-logs-*}")
    private String indexPattern;

    /**
     * Get user activities with filtering and statistics.
     * Returns only meaningful user actions.
     */
    public UserActivityResponse getUserActivities(
            String username,
            String role,
            int hours,
            int page,
            int size) {

        try {
            Instant from = Instant.now().minus(hours, ChronoUnit.HOURS);
            Instant to = Instant.now();

            // Build query for user activities
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            // Time range
            boolQuery.must(Query.of(q -> q
                    .range(r -> r
                            .field("@timestamp")
                            .gte(JsonData.of(from.toString()))
                            .lte(JsonData.of(to.toString())))));

            // Must have username (exclude anonymous/system)
            boolQuery.mustNot(Query.of(q -> q
                    .terms(t -> t
                            .field("username.keyword")
                            .terms(tf -> tf.value(Arrays.asList(
                                    FieldValue.of(""),
                                    FieldValue.of("anonymous"),
                                    FieldValue.of("system"),
                                    FieldValue.of("actuator")))))));

            // Must have username field
            boolQuery.must(Query.of(q -> q.exists(e -> e.field("username"))));

            // Filter by specific username
            if (username != null && !username.isBlank()) {
                boolQuery.must(Query.of(q -> q
                        .term(t -> t.field("username.keyword").value(username))));
            }

            // Filter by role
            if (role != null && !role.isBlank()) {
                boolQuery.must(Query.of(q -> q
                        .wildcard(w -> w.field("roles").value("*" + role + "*"))));
            }

            // Only show meaningful actions (exclude health checks, metrics, etc.)
            boolQuery.mustNot(Query.of(q -> q
                    .terms(t -> t
                            .field("path.keyword")
                            .terms(tf -> tf.value(Arrays.asList(
                                    FieldValue.of("/actuator/health"),
                                    FieldValue.of("/actuator/prometheus"),
                                    FieldValue.of("/actuator/info"),
                                    FieldValue.of("/metrics"),
                                    FieldValue.of("/health")))))));

            // Build aggregations for statistics
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexPattern)
                    .query(Query.of(q -> q.bool(boolQuery.build())))
                    .from(page * size)
                    .size(size)
                    .sort(so -> so.field(f -> f.field("@timestamp").order(SortOrder.Desc)))
                    .aggregations("by_user", agg -> agg
                            .terms(t -> t.field("username.keyword").size(100)))
                    .aggregations("by_service", agg -> agg
                            .terms(t -> t.field("service.keyword").size(20)))
                    .aggregations("by_action", agg -> agg
                            .terms(t -> t.field("action.keyword").size(20)))
                    .aggregations("error_count", agg -> agg
                            .filter(f -> f.term(t -> t.field("level.keyword").value("ERROR")))));

            // Execute search
            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            // Convert hits to UserActivityEntry
            List<UserActivityEntry> activities = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                activities.add(mapToActivityEntry(hit.id(), hit.source()));
            }

            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            int totalPages = (int) Math.ceil((double) total / size);

            // Build summary
            ActivitySummary summary = buildSummary(response, activities);

            return UserActivityResponse.builder()
                    .total(total)
                    .page(page)
                    .size(size)
                    .totalPages(totalPages)
                    .activities(activities)
                    .summary(summary)
                    .build();

        } catch (IOException e) {
            log.error("Failed to get user activities: {}", e.getMessage(), e);
            return UserActivityResponse.builder()
                    .total(0)
                    .page(0)
                    .size(size)
                    .totalPages(0)
                    .activities(Collections.emptyList())
                    .build();
        }
    }

    /**
     * Get activity summary statistics only.
     */
    public ActivitySummary getActivitySummary(int hours) {
        UserActivityResponse response = getUserActivities(null, null, hours, 0, 1);
        return response.getSummary();
    }

    /**
     * Map Elasticsearch document to UserActivityEntry.
     */
    private UserActivityEntry mapToActivityEntry(String id, Map<String, Object> source) {
        // Parse timestamp string to Instant
        Instant timestamp = null;
        String timestampStr = getStringValue(source, "@timestamp");
        if (timestampStr != null) {
            try {
                timestamp = Instant.parse(timestampStr);
            } catch (Exception e) {
                log.warn("Failed to parse timestamp: {}", timestampStr);
            }
        }

        return UserActivityEntry.builder()
                .id(id)
                .timestamp(timestamp)
                .username(getStringValue(source, "username", "user"))
                .roles(getStringValue(source, "roles"))
                .service(getStringValue(source, "service", "service_name"))
                .method(getStringValue(source, "method", "http_method"))
                .path(getStringValue(source, "path", "request_path"))
                .action(getStringValue(source, "action", "log_message", "message"))
                .requestId(getStringValue(source, "request_id", "requestId"))
                .level(getStringValue(source, "level", "log_level"))
                .isError("ERROR".equalsIgnoreCase(getStringValue(source, "level", "log_level")))
                .build();
    }

    /**
     * Build activity summary from search response.
     */
    private ActivitySummary buildSummary(SearchResponse<Map> response, List<UserActivityEntry> activities) {
        ActivitySummary.ActivitySummaryBuilder summaryBuilder = ActivitySummary.builder();

        // Active users count
        var userBuckets = response.aggregations().get("by_user");
        if (userBuckets != null && userBuckets.isSterms()) {
            int activeUsers = userBuckets.sterms().buckets().array().size();
            summaryBuilder.activeUsers(activeUsers);

            // Actions by user
            Map<String, Integer> actionsByUser = userBuckets.sterms().buckets().array().stream()
                    .collect(Collectors.toMap(
                            bucket -> bucket.key().stringValue(),
                            bucket -> (int) bucket.docCount()));
            summaryBuilder.actionsByUser(actionsByUser);
        } else {
            summaryBuilder.activeUsers(0).actionsByUser(Collections.emptyMap());
        }

        // Actions by service
        var serviceBuckets = response.aggregations().get("by_service");
        if (serviceBuckets != null && serviceBuckets.isSterms()) {
            Map<String, Integer> actionsByService = serviceBuckets.sterms().buckets().array().stream()
                    .collect(Collectors.toMap(
                            bucket -> bucket.key().stringValue(),
                            bucket -> (int) bucket.docCount()));
            summaryBuilder.actionsByService(actionsByService);
        } else {
            summaryBuilder.actionsByService(Collections.emptyMap());
        }

        // Top actions
        var actionBuckets = response.aggregations().get("by_action");
        if (actionBuckets != null && actionBuckets.isSterms()) {
            Map<String, Integer> topActions = actionBuckets.sterms().buckets().array().stream()
                    .limit(10)
                    .collect(Collectors.toMap(
                            bucket -> bucket.key().stringValue(),
                            bucket -> (int) bucket.docCount()));
            summaryBuilder.topActions(topActions);
        } else {
            summaryBuilder.topActions(Collections.emptyMap());
        }

        // Error count
        var errorAgg = response.aggregations().get("error_count");
        int errorCount = 0;
        if (errorAgg != null && errorAgg.isFilter()) {
            errorCount = (int) errorAgg.filter().docCount();
        }
        summaryBuilder.errorCount(errorCount);

        // Total actions - use total from search response, not paginated activities list
        long totalActions = response.hits().total() != null ? response.hits().total().value() : 0;
        summaryBuilder.totalActions(totalActions);

        return summaryBuilder.build();
    }

    /**
     * Get string value from map, trying multiple field names.
     */
    private String getStringValue(Map<String, Object> map, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Object value = map.get(fieldName);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }
}
