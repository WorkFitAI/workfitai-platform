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
import org.workfitai.monitoringservice.dto.ActivitySummary;
import org.workfitai.monitoringservice.dto.UserActivityEntry;
import org.workfitai.monitoringservice.dto.UserActivityResponse;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service for admin-facing user activity queries.
 * Focuses on meaningful user actions, not system logs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminActivityService {

    private final ElasticsearchClient elasticsearchClient;
    private final ActivityMessageFormatter messageFormatter;

    @Value("${elasticsearch.index-pattern:workfitai-logs-*}")
    private String indexPattern;

    // ─── Public API ───────────────────────────────────────────────────────────────

    public UserActivityResponse getUserActivities(String username, String role, int hours, int page, int size) {
        try {
            Instant from = Instant.now().minus(hours, ChronoUnit.HOURS);
            Instant to = Instant.now();
            BoolQuery.Builder boolQuery = buildUserActivityBoolQuery(username, role, from, to);
            SearchRequest searchRequest = buildUserActivitySearchRequest(boolQuery, page, size);
            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);
            List<UserActivityEntry> activities = convertHitsToActivities(response.hits().hits());
            return buildUserActivityResponse(response, page, size, activities);
        } catch (IOException e) {
            log.error("Failed to get user activities: {}", e.getMessage(), e);
            return UserActivityResponse.builder()
                    .total(0).page(0).size(size).totalPages(0)
                    .activities(Collections.emptyList())
                    .build();
        }
    }

    public ActivitySummary getActivitySummary(int hours) {
        return getUserActivities(null, null, hours, 0, 1).getSummary();
    }

    // ─── Query builders ───────────────────────────────────────────────────────────

    private BoolQuery.Builder buildUserActivityBoolQuery(String username, String role,
                                                          Instant from, Instant to) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();
        addTimeRangeFilter(boolQuery, from, to);
        addLogTypeFilter(boolQuery);
        addExcludedUsersFilter(boolQuery);
        addRequiredUsernameFilter(boolQuery);
        if (username != null && !username.isBlank()) {
            boolQuery.must(Query.of(q -> q.term(t -> t.field("username.keyword").value(username))));
        }
        if (role != null && !role.isBlank()) {
            boolQuery.must(Query.of(q -> q.wildcard(w -> w.field("roles").value("*" + role + "*"))));
        }
        addExcludedPathsFilter(boolQuery);
        addRequiredRequestIdFilter(boolQuery);
        addExcludedMessageFilters(boolQuery);
        addExcludedLoggerFilters(boolQuery);
        return boolQuery;
    }

    private void addTimeRangeFilter(BoolQuery.Builder boolQuery, Instant from, Instant to) {
        boolQuery.must(Query.of(q -> q
                .range(r -> r.field("@timestamp")
                        .gte(JsonData.of(from.toString()))
                        .lte(JsonData.of(to.toString())))));
    }

    private void addLogTypeFilter(BoolQuery.Builder boolQuery) {
        boolQuery.must(Query.of(q -> q.term(t -> t.field("log_type.keyword").value("USER_ACTION"))));
    }

    private void addExcludedUsersFilter(BoolQuery.Builder boolQuery) {
        boolQuery.mustNot(Query.of(q -> q
                .terms(t -> t.field("username.keyword")
                        .terms(tf -> tf.value(Arrays.asList(
                                FieldValue.of(""), FieldValue.of("anonymous"),
                                FieldValue.of("system"), FieldValue.of("actuator")))))));
    }

    private void addRequiredUsernameFilter(BoolQuery.Builder boolQuery) {
        boolQuery.must(Query.of(q -> q.exists(e -> e.field("username"))));
    }

    private void addExcludedPathsFilter(BoolQuery.Builder boolQuery) {
        boolQuery.mustNot(Query.of(q -> q
                .terms(t -> t.field("path.keyword")
                        .terms(tf -> tf.value(Arrays.asList(
                                FieldValue.of("/actuator/health"), FieldValue.of("/actuator/prometheus"),
                                FieldValue.of("/actuator/info"), FieldValue.of("/metrics"),
                                FieldValue.of("/health")))))));
    }

    private void addRequiredRequestIdFilter(BoolQuery.Builder boolQuery) {
        boolQuery.must(Query.of(q -> q.exists(e -> e.field("request_id"))));
    }

    private void addExcludedMessageFilters(BoolQuery.Builder boolQuery) {
        boolQuery.mustNot(Query.of(q -> q.wildcard(w -> w.field("message").value("*\\[DEBUG\\]*"))));
        boolQuery.mustNot(Query.of(q -> q.wildcard(w -> w.field("message").value("*completed \\[requestId=*"))));
        boolQuery.mustNot(Query.of(q -> q.wildcard(w -> w.field("message").value("*✅*"))));
        boolQuery.mustNot(Query.of(q -> q.wildcard(w -> w.field("message").value("*JwtClaimsFilter*"))));
        boolQuery.mustNot(Query.of(q -> q.wildcard(w -> w.field("message").value("*X-Token-Source*"))));
        boolQuery.mustNot(Query.of(q -> q.wildcard(w -> w.field("message").value("*X-Original-Opaque*"))));
        boolQuery.mustNot(Query.of(q -> q.wildcard(w -> w.field("message").value("*Authorization: Bearer*"))));
    }

    private void addExcludedLoggerFilters(BoolQuery.Builder boolQuery) {
        boolQuery.mustNot(Query.of(q -> q.wildcard(w -> w.field("logger_name").value("*filter*"))));
        boolQuery.mustNot(Query.of(q -> q.wildcard(w -> w.field("logger_name").value("*Filter*"))));
    }

    private SearchRequest buildUserActivitySearchRequest(BoolQuery.Builder boolQuery, int page, int size) {
        return SearchRequest.of(s -> s
                .index(indexPattern)
                .query(Query.of(q -> q.bool(boolQuery.build())))
                .from(page * size)
                .size(size)
                .sort(so -> so.field(f -> f.field("@timestamp").order(SortOrder.Desc)))
                .aggregations("by_user", agg -> agg.terms(t -> t.field("username.keyword").size(100)))
                .aggregations("by_service", agg -> agg.terms(t -> t.field("service.keyword").size(20)))
                .aggregations("by_action", agg -> agg.terms(t -> t.field("action.keyword").size(20)))
                .aggregations("error_count", agg -> agg
                        .filter(f -> f.term(t -> t.field("level.keyword").value("ERROR")))));
    }

    // ─── Response building ────────────────────────────────────────────────────────

    private List<UserActivityEntry> convertHitsToActivities(List<Hit<Map>> hits) {
        List<UserActivityEntry> activities = new ArrayList<>();
        for (Hit<Map> hit : hits) {
            activities.add(mapToActivityEntry(hit.id(), hit.source()));
        }
        return activities;
    }

    private UserActivityResponse buildUserActivityResponse(SearchResponse<Map> response,
                                                            int page, int size,
                                                            List<UserActivityEntry> activities) {
        long total = response.hits().total() != null ? response.hits().total().value() : 0;
        int totalPages = (int) Math.ceil((double) total / size);
        ActivitySummary summary = buildSummary(response, activities);
        return UserActivityResponse.builder()
                .total(total).page(page).size(size).totalPages(totalPages)
                .activities(activities)
                .summary(summary)
                .build();
    }

    // ─── Summary building ─────────────────────────────────────────────────────────

    private ActivitySummary buildSummary(SearchResponse<Map> response, List<UserActivityEntry> activities) {
        ActivitySummary.ActivitySummaryBuilder builder = ActivitySummary.builder();
        long totalActions = response.hits().total() != null ? response.hits().total().value() : 0;
        builder.totalActions(totalActions);
        log.debug("Building activity summary - total actions: {}", totalActions);
        extractUserStats(response, builder);
        extractServiceStats(response, builder);
        extractTopActions(response, activities, builder);
        extractErrorCount(response, builder);
        return builder.build();
    }

    private void extractUserStats(SearchResponse<Map> response,
                                   ActivitySummary.ActivitySummaryBuilder builder) {
        Map<String, Integer> actionsByUser = new LinkedHashMap<>();
        var userBuckets = response.aggregations().get("by_user");
        if (userBuckets != null && userBuckets.isSterms()) {
            List<StringTermsBucket> buckets = userBuckets.sterms().buckets().array();
            builder.activeUsers(buckets.size());
            buckets.forEach(b -> actionsByUser.put(b.key().stringValue(), (int) b.docCount()));
            log.debug("Found {} active users", buckets.size());
        } else {
            builder.activeUsers(0);
            log.warn("User aggregation not found or not sterms type");
        }
        builder.actionsByUser(actionsByUser);
    }

    private void extractServiceStats(SearchResponse<Map> response,
                                      ActivitySummary.ActivitySummaryBuilder builder) {
        Map<String, Integer> actionsByService = new LinkedHashMap<>();
        var serviceBuckets = response.aggregations().get("by_service");
        if (serviceBuckets != null && serviceBuckets.isSterms()) {
            serviceBuckets.sterms().buckets().array()
                    .forEach(b -> actionsByService.put(b.key().stringValue(), (int) b.docCount()));
        }
        builder.actionsByService(actionsByService);
    }

    private void extractTopActions(SearchResponse<Map> response, List<UserActivityEntry> activities,
                                    ActivitySummary.ActivitySummaryBuilder builder) {
        Map<String, Integer> topActions = new LinkedHashMap<>();
        var actionBuckets = response.aggregations().get("by_action");
        if (actionBuckets != null && actionBuckets.isSterms()) {
            List<StringTermsBucket> buckets = actionBuckets.sterms().buckets().array();
            int limit = Math.min(10, buckets.size());
            for (int i = 0; i < limit; i++) {
                StringTermsBucket bucket = buckets.get(i);
                String action = bucket.key().stringValue();
                String entityType = findEntityTypeForAction(activities, action);
                topActions.put(formatActionForDisplay(action, entityType), (int) bucket.docCount());
            }
        }
        builder.topActions(topActions);
    }

    private void extractErrorCount(SearchResponse<Map> response,
                                    ActivitySummary.ActivitySummaryBuilder builder) {
        var errorAgg = response.aggregations().get("error_count");
        int errorCount = (errorAgg != null && errorAgg.isFilter()) ? (int) errorAgg.filter().docCount() : 0;
        builder.errorCount(errorCount);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    private UserActivityEntry mapToActivityEntry(String id, Map<String, Object> source) {
        Instant timestamp = null;
        String timestampStr = getStringValue(source, "@timestamp");
        if (timestampStr != null) {
            try {
                timestamp = Instant.parse(timestampStr);
            } catch (Exception e) {
                log.warn("Failed to parse timestamp: {}", timestampStr);
            }
        }

        String method = getStringValue(source, "method", "http_method");
        String path = getStringValue(source, "path", "request_path");
        String action = getStringValue(source, "action");
        String entityType = getStringValue(source, "entity_type");
        String service = getStringValue(source, "service", "service_name");

        return UserActivityEntry.builder()
                .id(id)
                .timestamp(timestamp)
                .username(getStringValue(source, "username", "user"))
                .roles(getStringValue(source, "roles"))
                .service(service)
                .method(method)
                .path(path)
                .requestId(getStringValue(source, "request_id", "requestId"))
                .level(getStringValue(source, "level", "log_level"))
                .isError("ERROR".equalsIgnoreCase(getStringValue(source, "level", "log_level")))
                .logType(getStringValue(source, "log_type"))
                .action(action)
                .entityType(entityType)
                .entityId(getStringValue(source, "entity_id"))
                .message(getStringValue(source, "log_message", "message"))
                .displayMessage(messageFormatter.formatActivityMessage(method, path, action, entityType, service))
                .relativeTime(messageFormatter.formatRelativeTime(timestamp))
                .icon(messageFormatter.getActivityIcon(action, entityType))
                .build();
    }

    private String getStringValue(Map<String, Object> map, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Object value = map.get(fieldName);
            if (value != null) return value.toString();
        }
        return null;
    }

    private String findEntityTypeForAction(List<UserActivityEntry> activities, String action) {
        if (activities == null || action == null) return null;
        return activities.stream()
                .filter(a -> action.equals(a.getAction()))
                .map(UserActivityEntry::getEntityType)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String formatActionForDisplay(String action, String entityType) {
        if (action == null) return "Unknown";
        if (entityType != null && !entityType.isBlank()) {
            String formatted = messageFormatter.formatByAction(action, entityType);
            if (formatted != null) return formatted;
        }
        String formatted = messageFormatter.formatByAction(action, "");
        return formatted != null ? formatted : action;
    }
}
