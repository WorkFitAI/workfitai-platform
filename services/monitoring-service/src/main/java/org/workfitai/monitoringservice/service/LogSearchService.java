package org.workfitai.monitoringservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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
 * Service for querying and analyzing logs from Elasticsearch.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogSearchService {

    private final ElasticsearchClient elasticsearchClient;

    @Value("${elasticsearch.index-pattern:workfitai-logs-*}")
    private String indexPattern;

    /**
     * Search logs with flexible filtering.
     */
    public LogSearchResponse searchLogs(LogSearchRequest request) {
        try {
            // Build query
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            // Free text search
            if (request.getQuery() != null && !request.getQuery().isBlank()) {
                boolQuery.must(Query.of(q -> q
                        .multiMatch(mm -> mm
                                .query(request.getQuery())
                                .fields("log_message", "message", "logger", "logger_name", "stack_trace"))));
            }

            // Filter by service (try both field names)
            if (request.getService() != null && !request.getService().isBlank()) {
                String serviceName = request.getService();
                boolQuery.filter(Query.of(q -> q
                        .bool(b -> b
                                .should(Query
                                        .of(sq -> sq.term(t -> t.field("service_name.keyword").value(serviceName))))
                                .should(Query.of(sq -> sq.term(t -> t.field("service.keyword").value(serviceName))))
                                .should(Query.of(sq -> sq.term(t -> t.field("service").value(serviceName))))
                                .minimumShouldMatch("1"))));
            }

            // Filter by log levels (try both field names)
            if (request.getLevels() != null && !request.getLevels().isEmpty()) {
                List<String> levels = request.getLevels().stream()
                        .map(String::toUpperCase)
                        .toList();
                boolQuery.filter(Query.of(q -> q
                        .bool(b -> b
                                .should(Query.of(sq -> sq.terms(t -> t
                                        .field("log_level.keyword")
                                        .terms(tv -> tv.value(levels.stream()
                                                .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                                .toList())))))
                                .should(Query.of(sq -> sq.terms(t -> t
                                        .field("level.keyword")
                                        .terms(tv -> tv.value(levels.stream()
                                                .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                                .toList())))))
                                .minimumShouldMatch("1"))));
            }

            // Filter by trace ID
            if (request.getTraceId() != null && !request.getTraceId().isBlank()) {
                String traceId = request.getTraceId();
                boolQuery.filter(Query.of(q -> q
                        .bool(b -> b
                                .should(Query.of(sq -> sq.term(t -> t.field("trace_id").value(traceId))))
                                .should(Query.of(sq -> sq.term(t -> t.field("traceId").value(traceId))))
                                .minimumShouldMatch("1"))));
            }

            // Filter by user ID
            if (request.getUserId() != null && !request.getUserId().isBlank()) {
                String userId = request.getUserId();
                boolQuery.filter(Query.of(q -> q
                        .bool(b -> b
                                .should(Query.of(sq -> sq.term(t -> t.field("user_id").value(userId))))
                                .should(Query.of(sq -> sq.term(t -> t.field("userId").value(userId))))
                                .minimumShouldMatch("1"))));
            }

            // Filter by username
            if (request.getUsername() != null && !request.getUsername().isBlank()) {
                String username = request.getUsername();
                boolQuery.filter(Query.of(q -> q
                        .bool(b -> b
                                .should(Query.of(sq -> sq.term(t -> t.field("user.keyword").value(username))))
                                .should(Query.of(sq -> sq.term(t -> t.field("username.keyword").value(username))))
                                .minimumShouldMatch("1"))));
            }

            // Filter by request ID
            if (request.getRequestId() != null && !request.getRequestId().isBlank()) {
                String requestId = request.getRequestId();
                boolQuery.filter(Query.of(q -> q
                        .bool(b -> b
                                .should(Query.of(sq -> sq.term(t -> t.field("request_id.keyword").value(requestId))))
                                .should(Query.of(sq -> sq.term(t -> t.field("request_id").value(requestId))))
                                .should(Query.of(sq -> sq.term(t -> t.field("requestId.keyword").value(requestId))))
                                .should(Query.of(sq -> sq.term(t -> t.field("requestId").value(requestId))))
                                .minimumShouldMatch("1"))));
            }

            // Time range filter
            Instant from = request.getFrom() != null ? request.getFrom() : Instant.now().minus(24, ChronoUnit.HOURS);
            Instant to = request.getTo() != null ? request.getTo() : Instant.now();

            boolQuery.filter(Query.of(q -> q
                    .range(r -> r
                            .field("@timestamp")
                            .gte(JsonData.of(from.toString()))
                            .lte(JsonData.of(to.toString())))));

            // Build search request
            SortOrder sortOrder = "asc".equalsIgnoreCase(request.getSortOrder()) ? SortOrder.Asc : SortOrder.Desc;

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexPattern)
                    .query(Query.of(q -> q.bool(boolQuery.build())))
                    .from(request.getPage() * request.getSize())
                    .size(request.getSize())
                    .sort(so -> so.field(f -> f.field("@timestamp").order(sortOrder))));

            // Execute search
            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);

            // Convert hits to LogEntry
            List<LogEntry> logs = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                logs.add(mapToLogEntry(hit.id(), hit.source()));
            }

            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            int totalPages = (int) Math.ceil((double) total / request.getSize());

            return LogSearchResponse.builder()
                    .total(total)
                    .page(request.getPage())
                    .size(request.getSize())
                    .totalPages(totalPages)
                    .logs(logs)
                    .build();

        } catch (IOException e) {
            log.error("Failed to search logs: {}", e.getMessage(), e);
            return LogSearchResponse.builder()
                    .total(0)
                    .logs(Collections.emptyList())
                    .build();
        }
    }

    /**
     * Get log statistics for dashboard.
     */
    public LogStatistics getStatistics(int hours) {
        try {
            Instant from = Instant.now().minus(hours, ChronoUnit.HOURS);

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexPattern)
                    .size(0)
                    .query(Query.of(q -> q
                            .range(r -> r
                                    .field("@timestamp")
                                    .gte(JsonData.of(from.toString())))))
                    .aggregations("by_level", a -> a
                            .terms(t -> t.field("level.keyword").size(10)))
                    .aggregations("by_service", a -> a
                            .terms(t -> t.field("service.keyword").size(20))));

            SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);

            long totalLogs = response.hits().total() != null ? response.hits().total().value() : 0;

            // Parse level aggregation
            Map<String, Long> byLevel = new HashMap<>();
            if (response.aggregations().get("by_level") != null) {
                for (StringTermsBucket bucket : response.aggregations().get("by_level").sterms().buckets().array()) {
                    byLevel.put(bucket.key().stringValue(), bucket.docCount());
                }
            }

            // Parse service aggregation
            Map<String, Long> byService = new HashMap<>();
            if (response.aggregations().get("by_service") != null) {
                for (StringTermsBucket bucket : response.aggregations().get("by_service").sterms().buckets().array()) {
                    byService.put(bucket.key().stringValue(), bucket.docCount());
                }
            }

            // Calculate error rate
            long errorCount = byLevel.getOrDefault("ERROR", 0L);
            double errorRate = totalLogs > 0 ? (double) errorCount / totalLogs * 100 : 0;

            // Calculate logs per minute
            double logsPerMinute = totalLogs > 0 ? (double) totalLogs / (hours * 60) : 0;

            return LogStatistics.builder()
                    .totalLogs(totalLogs)
                    .byLevel(byLevel)
                    .byService(byService)
                    .errorRate(Math.round(errorRate * 100.0) / 100.0)
                    .timeRangeHours(hours)
                    .logsPerMinute(Math.round(logsPerMinute * 100.0) / 100.0)
                    .build();

        } catch (IOException e) {
            log.error("Failed to get log statistics: {}", e.getMessage(), e);
            return LogStatistics.builder()
                    .totalLogs(0)
                    .byLevel(Collections.emptyMap())
                    .byService(Collections.emptyMap())
                    .build();
        }
    }

    /**
     * Get logs by trace ID for distributed tracing.
     */
    public List<LogEntry> getLogsByTraceId(String traceId) {
        LogSearchRequest request = LogSearchRequest.builder()
                .traceId(traceId)
                .size(1000)
                .sortOrder("asc")
                .build();
        return searchLogs(request).getLogs();
    }

    /**
     * Get recent errors for alerting.
     */
    public List<LogEntry> getRecentErrors(int minutes) {
        LogSearchRequest request = LogSearchRequest.builder()
                .levels(List.of("ERROR"))
                .from(Instant.now().minus(minutes, ChronoUnit.MINUTES))
                .to(Instant.now())
                .size(100)
                .sortOrder("desc")
                .build();
        return searchLogs(request).getLogs();
    }

    private LogEntry mapToLogEntry(String id, Map<String, Object> source) {
        if (source == null) {
            return LogEntry.builder().id(id).build();
        }

        // Map from Fluent Bit normalized field names to LogEntry
        // Fluent Bit uses: service_name, log_level, log_message, http_method,
        // http_path, request_id
        // Also fallback to original Spring Boot names: service, level, message
        return LogEntry.builder()
                .id(id)
                .timestamp(parseTimestamp(source.get("@timestamp")))
                // Service & Level - try normalized names first, then fallback
                .level(getStringWithFallback(source, "log_level", "level"))
                .service(getStringWithFallback(source, "service_name", "service"))
                // Message - try log_message first, then message
                .message(getStringWithFallback(source, "log_message", "message"))
                // Logger - try logger_class (short name), then logger, then logger_name
                .logger(getStringWithFallback(source, "logger_class", "logger", "logger_name"))
                // Thread
                .thread(getStringWithFallback(source, "thread", "thread_name"))
                // HTTP request info
                .method(getStringWithFallback(source, "http_method", "method"))
                .path(getStringWithFallback(source, "http_path", "path"))
                .requestId(getStringWithFallback(source, "request_id", "requestId"))
                // User info
                .userId(getStringWithFallback(source, "user_id", "userId"))
                .username(getStringWithFallback(source, "user", "username"))
                .roles(getStringWithFallback(source, "user_roles", "roles"))
                // Distributed tracing
                .traceId(getStringWithFallback(source, "trace_id", "traceId"))
                .spanId(getStringWithFallback(source, "span_id", "spanId"))
                // Exception info
                .exception(getStringWithFallback(source, "exception", "exception_class"))
                .stackTrace(getString(source, "stack_trace"))
                .build();
    }

    private String getString(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Get string value trying multiple keys in order (fallback mechanism).
     */
    private String getStringWithFallback(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                String str = value.toString();
                if (!str.isBlank()) {
                    return str;
                }
            }
        }
        return null;
    }

    private Instant parseTimestamp(Object value) {
        if (value == null)
            return null;
        try {
            return Instant.parse(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get user activity for admin dashboard.
     * Filters logs to show only user-initiated actions (excludes system logs,
     * health checks).
     */
    public UserActivityResponse getUserActivity(LogSearchRequest request, boolean includeSummary) {
        try {
            // Modify request to filter only user activities
            LogSearchRequest activityRequest = LogSearchRequest.builder()
                    .query(request.getQuery())
                    .service(request.getService())
                    .levels(request.getLevels())
                    .from(request.getFrom() != null ? request.getFrom() : Instant.now().minus(24, ChronoUnit.HOURS))
                    .to(request.getTo() != null ? request.getTo() : Instant.now())
                    .traceId(request.getTraceId())
                    .userId(request.getUserId())
                    .username(request.getUsername())
                    .page(request.getPage())
                    .size(request.getSize())
                    .sortOrder(request.getSortOrder())
                    .build();

            // Get logs
            LogSearchResponse logResponse = searchLogs(activityRequest);

            // Convert to UserActivityEntry and filter out system logs
            List<UserActivityEntry> activities = logResponse.getLogs().stream()
                    .filter(this::isUserActivity) // Filter out health checks, system logs
                    .map(UserActivityEntry::fromLogEntry)
                    .collect(Collectors.toList());

            // Build response
            UserActivityResponse.UserActivityResponseBuilder responseBuilder = UserActivityResponse.builder()
                    .total(logResponse.getTotal())
                    .page(logResponse.getPage())
                    .size(logResponse.getSize())
                    .totalPages(logResponse.getTotalPages())
                    .activities(activities);

            // Add summary if requested
            if (includeSummary) {
                responseBuilder.summary(calculateActivitySummary(activities, activityRequest));
            }

            return responseBuilder.build();

        } catch (Exception e) {
            log.error("Failed to get user activity: {}", e.getMessage(), e);
            return UserActivityResponse.builder()
                    .total(0)
                    .activities(Collections.emptyList())
                    .build();
        }
    }

    /**
     * Check if a log entry represents user activity (not system/health check).
     */
    private boolean isUserActivity(LogEntry log) {
        // Must have a username (not anonymous or system)
        String username = log.getUsername();
        if (username == null || username.isBlank() || "anonymous".equalsIgnoreCase(username)) {
            return false;
        }

        // Exclude health checks and actuator endpoints
        String path = log.getPath();
        if (path != null) {
            String lowerPath = path.toLowerCase();
            if (lowerPath.contains("/health") ||
                    lowerPath.contains("/actuator") ||
                    lowerPath.contains("/metrics") ||
                    lowerPath.contains("/prometheus")) {
                return false;
            }
        }

        // Exclude DEBUG level logs for cleaner activity view
        String level = log.getLevel();
        if ("DEBUG".equalsIgnoreCase(level) || "TRACE".equalsIgnoreCase(level)) {
            return false;
        }

        return true;
    }

    /**
     * Calculate activity summary for dashboard widgets.
     */
    private UserActivityResponse.ActivitySummary calculateActivitySummary(
            List<UserActivityEntry> activities,
            LogSearchRequest request) {
        try {
            // Calculate from the activities we have
            Map<String, Long> actionsByUser = activities.stream()
                    .filter(a -> a.getUsername() != null)
                    .collect(Collectors.groupingBy(UserActivityEntry::getUsername, Collectors.counting()));

            Map<String, Long> actionsByService = activities.stream()
                    .filter(a -> a.getService() != null)
                    .collect(Collectors.groupingBy(UserActivityEntry::getService, Collectors.counting()));

            Map<String, Long> topActions = activities.stream()
                    .filter(a -> a.getAction() != null)
                    .collect(Collectors.groupingBy(UserActivityEntry::getAction, Collectors.counting()));

            // Sort and limit top actions
            topActions = topActions.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new));

            long errorCount = activities.stream()
                    .filter(a -> Boolean.TRUE.equals(a.getIsError()))
                    .count();

            return UserActivityResponse.ActivitySummary.builder()
                    .activeUsers(actionsByUser.size())
                    .totalActions(activities.size())
                    .errorCount(errorCount)
                    .actionsByUser(actionsByUser)
                    .actionsByService(actionsByService)
                    .topActions(topActions)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to calculate activity summary: {}", e.getMessage());
            return null;
        }
    }
}
