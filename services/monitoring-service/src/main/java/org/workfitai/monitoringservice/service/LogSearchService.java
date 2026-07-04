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
 * Service for querying and analyzing logs from Elasticsearch.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogSearchService {

    private final ElasticsearchClient elasticsearchClient;

    @Value("${elasticsearch.index-pattern:workfitai-logs-*}")
    private String indexPattern;

    // ─── Public API ───────────────────────────────────────────────────────────────

    public LogSearchResponse searchLogs(LogSearchRequest request) {
        try {
            BoolQuery.Builder boolQuery = buildLogBoolQuery(request);
            SearchResponse<Map> response = executeLogSearch(boolQuery, request);
            return buildLogSearchResponse(response, request);
        } catch (IOException e) {
            log.error("Failed to search logs: {}", e.getMessage(), e);
            return LogSearchResponse.builder().total(0).logs(Collections.emptyList()).build();
        }
    }

    public LogStatistics getStatistics(int hours) {
        try {
            BoolQuery.Builder boolQuery = buildStatsBoolQuery(hours);
            SearchResponse<Void> response = executeStatsSearch(boolQuery);
            return buildLogStatistics(response, hours);
        } catch (IOException e) {
            log.error("Failed to get log statistics: {}", e.getMessage(), e);
            return LogStatistics.builder()
                    .totalLogs(0)
                    .byLevel(Collections.emptyMap())
                    .byService(Collections.emptyMap())
                    .build();
        }
    }

    public List<LogEntry> getLogsByTraceId(String traceId) {
        LogSearchRequest request = LogSearchRequest.builder()
                .traceId(traceId)
                .size(1000)
                .sortOrder("asc")
                .build();
        return searchLogs(request).getLogs();
    }

    public List<LogEntry> getRecentErrors(int minutes) {
        LogSearchRequest request = LogSearchRequest.builder()
                .levels(List.of("ERROR"))
                .from(Instant.now().minus(minutes, ChronoUnit.MINUTES))
                .to(Instant.now())
                .size(100)
                .sortOrder("desc")
                .build();
        return searchLogs(request).getLogs().stream()
                .filter(this::isUserActivity)
                .collect(Collectors.toList());
    }

    // ─── Query builders ───────────────────────────────────────────────────────────

    private BoolQuery.Builder buildLogBoolQuery(LogSearchRequest request) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();
        addTextFilter(boolQuery, request.getQuery());
        addServiceFilter(boolQuery, request.getService());
        addLevelFilters(boolQuery, request.getLevels());
        addTraceFilter(boolQuery, request.getTraceId());
        addUserIdFilter(boolQuery, request.getUserId());
        addUsernameFilter(boolQuery, request.getUsername());
        addRequestIdFilter(boolQuery, request.getRequestId());
        addTimeRangeFilter(boolQuery, request.getFrom(), request.getTo());
        return boolQuery;
    }

    private void addTextFilter(BoolQuery.Builder boolQuery, String query) {
        if (query != null && !query.isBlank()) {
            boolQuery.must(Query.of(q -> q
                    .multiMatch(mm -> mm
                            .query(query)
                            .fields("log_message", "message", "logger", "logger_name", "stack_trace"))));
        }
    }

    private void addServiceFilter(BoolQuery.Builder boolQuery, String service) {
        if (service == null || service.isBlank()) return;
        boolQuery.filter(Query.of(q -> q
                .bool(b -> b
                        .should(Query.of(sq -> sq.term(t -> t.field("service_name.keyword").value(service))))
                        .should(Query.of(sq -> sq.term(t -> t.field("service.keyword").value(service))))
                        .should(Query.of(sq -> sq.term(t -> t.field("service").value(service))))
                        .minimumShouldMatch("1"))));
    }

    private void addLevelFilters(BoolQuery.Builder boolQuery, List<String> levels) {
        if (levels == null || levels.isEmpty()) return;
        List<String> upperLevels = levels.stream().map(String::toUpperCase).toList();
        List<FieldValue> fieldValues = upperLevels.stream().map(FieldValue::of).toList();
        boolQuery.filter(Query.of(q -> q
                .bool(b -> b
                        .should(Query.of(sq -> sq.terms(t -> t
                                .field("log_level.keyword")
                                .terms(tv -> tv.value(fieldValues)))))
                        .should(Query.of(sq -> sq.terms(t -> t
                                .field("level.keyword")
                                .terms(tv -> tv.value(fieldValues)))))
                        .minimumShouldMatch("1"))));
    }

    private void addTraceFilter(BoolQuery.Builder boolQuery, String traceId) {
        if (traceId == null || traceId.isBlank()) return;
        boolQuery.filter(Query.of(q -> q
                .bool(b -> b
                        .should(Query.of(sq -> sq.term(t -> t.field("trace_id").value(traceId))))
                        .should(Query.of(sq -> sq.term(t -> t.field("traceId").value(traceId))))
                        .minimumShouldMatch("1"))));
    }

    private void addUserIdFilter(BoolQuery.Builder boolQuery, String userId) {
        if (userId == null || userId.isBlank()) return;
        boolQuery.filter(Query.of(q -> q
                .bool(b -> b
                        .should(Query.of(sq -> sq.term(t -> t.field("user_id").value(userId))))
                        .should(Query.of(sq -> sq.term(t -> t.field("userId").value(userId))))
                        .minimumShouldMatch("1"))));
    }

    private void addUsernameFilter(BoolQuery.Builder boolQuery, String username) {
        if (username == null || username.isBlank()) return;
        boolQuery.filter(Query.of(q -> q
                .bool(b -> b
                        .should(Query.of(sq -> sq.term(t -> t.field("user.keyword").value(username))))
                        .should(Query.of(sq -> sq.term(t -> t.field("username.keyword").value(username))))
                        .minimumShouldMatch("1"))));
    }

    private void addRequestIdFilter(BoolQuery.Builder boolQuery, String requestId) {
        if (requestId == null || requestId.isBlank()) return;
        boolQuery.filter(Query.of(q -> q
                .bool(b -> b
                        .should(Query.of(sq -> sq.term(t -> t.field("request_id.keyword").value(requestId))))
                        .should(Query.of(sq -> sq.term(t -> t.field("request_id").value(requestId))))
                        .should(Query.of(sq -> sq.term(t -> t.field("requestId.keyword").value(requestId))))
                        .should(Query.of(sq -> sq.term(t -> t.field("requestId").value(requestId))))
                        .minimumShouldMatch("1"))));
    }

    private void addTimeRangeFilter(BoolQuery.Builder boolQuery, Instant from, Instant to) {
        Instant rangeFrom = from != null ? from : Instant.now().minus(24, ChronoUnit.HOURS);
        Instant rangeTo = to != null ? to : Instant.now();
        boolQuery.filter(Query.of(q -> q
                .range(r -> r
                        .field("@timestamp")
                        .gte(JsonData.of(rangeFrom.toString()))
                        .lte(JsonData.of(rangeTo.toString())))));
    }

    // ─── Search execution ─────────────────────────────────────────────────────────

    private SearchResponse<Map> executeLogSearch(BoolQuery.Builder boolQuery, LogSearchRequest request)
            throws IOException {
        SortOrder sortOrder = "asc".equalsIgnoreCase(request.getSortOrder()) ? SortOrder.Asc : SortOrder.Desc;
        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(indexPattern)
                .query(Query.of(q -> q.bool(boolQuery.build())))
                .from(request.getPage() * request.getSize())
                .size(request.getSize())
                .sort(so -> so.field(f -> f.field("@timestamp").order(sortOrder))));
        return elasticsearchClient.search(searchRequest, Map.class);
    }

    private LogSearchResponse buildLogSearchResponse(SearchResponse<Map> response, LogSearchRequest request) {
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
    }

    // ─── Statistics ───────────────────────────────────────────────────────────────

    private BoolQuery.Builder buildStatsBoolQuery(int hours) {
        Instant from = Instant.now().minus(hours, ChronoUnit.HOURS);
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();
        boolQuery.must(Query.of(q -> q
                .range(r -> r.field("@timestamp").gte(JsonData.of(from.toString())))));
        boolQuery.mustNot(Query.of(q -> q
                .terms(t -> t.field("username.keyword")
                        .terms(tf -> tf.value(List.of(
                                FieldValue.of(""), FieldValue.of("anonymous"), FieldValue.of("system")))))));
        boolQuery.mustNot(Query.of(q -> q.wildcard(w -> w.field("path.keyword").wildcard("*/actuator*"))));
        boolQuery.mustNot(Query.of(q -> q.wildcard(w -> w.field("path.keyword").wildcard("*/health*"))));
        boolQuery.mustNot(Query.of(q -> q.wildcard(w -> w.field("path.keyword").wildcard("*/api/logs/*"))));
        boolQuery.mustNot(Query.of(q -> q
                .terms(t -> t.field("level.keyword")
                        .terms(tf -> tf.value(List.of(FieldValue.of("DEBUG"), FieldValue.of("TRACE")))))));
        return boolQuery;
    }

    private SearchResponse<Void> executeStatsSearch(BoolQuery.Builder boolQuery) throws IOException {
        return elasticsearchClient.search(s -> s
                .index(indexPattern)
                .size(0)
                .query(Query.of(q -> q.bool(boolQuery.build())))
                .aggregations("by_level", a -> a.terms(t -> t.field("level.keyword").size(10)))
                .aggregations("by_service", a -> a.terms(t -> t.field("service.keyword").size(20))),
                Void.class);
    }

    private LogStatistics buildLogStatistics(SearchResponse<Void> response, int hours) {
        long totalLogs = response.hits().total() != null ? response.hits().total().value() : 0;

        Map<String, Long> byLevel = new HashMap<>();
        if (response.aggregations().get("by_level") != null) {
            for (StringTermsBucket b : response.aggregations().get("by_level").sterms().buckets().array()) {
                byLevel.put(b.key().stringValue(), b.docCount());
            }
        }

        Map<String, Long> byService = new HashMap<>();
        if (response.aggregations().get("by_service") != null) {
            for (StringTermsBucket b : response.aggregations().get("by_service").sterms().buckets().array()) {
                byService.put(b.key().stringValue(), b.docCount());
            }
        }

        long errorCount = byLevel.getOrDefault("ERROR", 0L);
        double errorRate = totalLogs > 0 ? (double) errorCount / totalLogs * 100 : 0;
        double logsPerMinute = totalLogs > 0 ? (double) totalLogs / (hours * 60) : 0;

        return LogStatistics.builder()
                .totalLogs(totalLogs)
                .byLevel(byLevel)
                .byService(byService)
                .errorRate(Math.round(errorRate * 100.0) / 100.0)
                .timeRangeHours(hours)
                .logsPerMinute(Math.round(logsPerMinute * 100.0) / 100.0)
                .build();
    }

    // ─── User activity ────────────────────────────────────────────────────────────

    /**
     * Returns true for user-initiated requests; false for system/health/infrastructure logs.
     */
    private boolean isUserActivity(LogEntry log) {
        String username = log.getUsername();
        if (username == null || username.isBlank()
                || "anonymous".equalsIgnoreCase(username)
                || "system".equalsIgnoreCase(username)) {
            return false;
        }
        if (isSystemPath(log.getPath())) return false;

        String level = log.getLevel();
        if ("DEBUG".equalsIgnoreCase(level) || "TRACE".equalsIgnoreCase(level)) return false;

        if (isExcludedMessage(log.getMessage())) return false;

        String method = log.getMethod();
        if (method != null && !method.matches("GET|POST|PUT|PATCH|DELETE")) return false;

        return true;
    }

    private boolean isSystemPath(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        if (lower.contains("/health") || lower.contains("/actuator")
                || lower.contains("/metrics") || lower.contains("/prometheus")
                || lower.contains("/info")) {
            return true;
        }
        if (lower.contains("/reindex") || lower.contains("/sync")
                || lower.contains("/scheduler") || lower.contains("/cron")
                || lower.contains("/batch")) {
            return true;
        }
        if (lower.contains("/poll") || lower.contains("/refresh")
                || lower.contains("/heartbeat") || lower.contains("/ping")) {
            return true;
        }
        if (lower.contains("/api/logs/activity") || lower.contains("/api/logs/stats")
                || lower.contains("/api/logs/online") || lower.contains("/api/logs/search")
                || lower.contains("/api/logs/errors")) {
            return true;
        }
        return lower.contains("/notification/unread-count") || lower.contains("/unread-count");
    }

    private boolean isExcludedMessage(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("health check") || lower.contains("scheduled task")
                || lower.contains("background job") || lower.contains("auto-refresh")
                || lower.contains("elasticsearch health");
    }

    // ─── Document mapping ─────────────────────────────────────────────────────────

    private LogEntry mapToLogEntry(String id, Map<String, Object> source) {
        if (source == null) {
            return LogEntry.builder().id(id).build();
        }
        return LogEntry.builder()
                .id(id)
                .timestamp(parseTimestamp(source.get("@timestamp")))
                .level(getStringWithFallback(source, "log_level", "level"))
                .service(getStringWithFallback(source, "service_name", "service"))
                .message(getStringWithFallback(source, "log_message", "message"))
                .logger(getStringWithFallback(source, "logger_class", "logger", "logger_name"))
                .thread(getStringWithFallback(source, "thread", "thread_name"))
                .method(getStringWithFallback(source, "http_method", "method"))
                .path(getStringWithFallback(source, "http_path", "path"))
                .requestId(getStringWithFallback(source, "request_id", "requestId"))
                .userId(getStringWithFallback(source, "user_id", "userId"))
                .username(getStringWithFallback(source, "user", "username"))
                .roles(getStringWithFallback(source, "user_roles", "roles"))
                .traceId(getStringWithFallback(source, "trace_id", "traceId"))
                .spanId(getStringWithFallback(source, "span_id", "spanId"))
                .exception(getStringWithFallback(source, "exception", "exception_class"))
                .stackTrace(getString(source, "stack_trace"))
                .build();
    }

    private String getString(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value != null ? value.toString() : null;
    }

    private String getStringWithFallback(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                String str = value.toString();
                if (!str.isBlank()) return str;
            }
        }
        return null;
    }

    private Instant parseTimestamp(Object value) {
        if (value == null) return null;
        try {
            return Instant.parse(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
