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
import org.workfitai.monitoringservice.dto.LogEntry;
import org.workfitai.monitoringservice.dto.LogSearchRequest;
import org.workfitai.monitoringservice.dto.LogSearchResponse;
import org.workfitai.monitoringservice.dto.LogStatistics;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

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
                                .fields("message", "logger_name", "stack_trace"))));
            }

            // Filter by service
            if (request.getService() != null && !request.getService().isBlank()) {
                boolQuery.filter(Query.of(q -> q
                        .term(t -> t.field("service").value(request.getService()))));
            }

            // Filter by log levels
            if (request.getLevels() != null && !request.getLevels().isEmpty()) {
                boolQuery.filter(Query.of(q -> q
                        .terms(t -> t
                                .field("level")
                                .terms(tv -> tv.value(request.getLevels().stream()
                                        .map(l -> co.elastic.clients.elasticsearch._types.FieldValue.of(l))
                                        .toList())))));
            }

            // Filter by trace ID
            if (request.getTraceId() != null && !request.getTraceId().isBlank()) {
                boolQuery.filter(Query.of(q -> q
                        .term(t -> t.field("traceId").value(request.getTraceId()))));
            }

            // Filter by user ID
            if (request.getUserId() != null && !request.getUserId().isBlank()) {
                boolQuery.filter(Query.of(q -> q
                        .term(t -> t.field("userId").value(request.getUserId()))));
            }

            // Filter by username
            if (request.getUsername() != null && !request.getUsername().isBlank()) {
                boolQuery.filter(Query.of(q -> q
                        .term(t -> t.field("username").value(request.getUsername()))));
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

        return LogEntry.builder()
                .id(id)
                .timestamp(parseTimestamp(source.get("@timestamp")))
                .level(getString(source, "level"))
                .service(getString(source, "service"))
                .logger(getString(source, "logger_name"))
                .message(getString(source, "message"))
                .thread(getString(source, "thread_name"))
                .traceId(getString(source, "traceId"))
                .spanId(getString(source, "spanId"))
                .userId(getString(source, "userId"))
                .username(getString(source, "username"))
                .exception(getString(source, "exception_class"))
                .stackTrace(getString(source, "stack_trace"))
                .build();
    }

    private String getString(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value != null ? value.toString() : null;
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
}
