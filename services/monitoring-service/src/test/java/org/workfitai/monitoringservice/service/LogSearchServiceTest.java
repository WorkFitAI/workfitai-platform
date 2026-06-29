package org.workfitai.monitoringservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.monitoringservice.dto.LogSearchRequest;
import org.workfitai.monitoringservice.dto.LogSearchResponse;
import org.workfitai.monitoringservice.dto.LogStatistics;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogSearchServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    private LogSearchService logSearchService;

    private void init() {
        logSearchService = new LogSearchService(elasticsearchClient);
        ReflectionTestUtils.setField(logSearchService, "indexPattern", "workfitai-logs-*");
    }

    @SuppressWarnings("unchecked")
    private Hit<Map> hitWith(Map<String, Object> source) {
        Hit<Map> hit = mock(Hit.class);
        when(hit.id()).thenReturn("log-1");
        when(hit.source()).thenReturn(source);
        return hit;
    }

    @Test
    void searchLogs_returnsEmptyResponse_onIOException() throws IOException {
        init();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenThrow(new IOException("es down"));

        LogSearchResponse result = logSearchService.searchLogs(LogSearchRequest.builder().build());

        assertThat(result.getTotal()).isZero();
        assertThat(result.getLogs()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchLogs_mapsHitsToLogEntries_onSuccess() throws IOException {
        init();
        Hit<Map> hit = hitWith(Map.of(
                "@timestamp", "2024-01-01T00:00:00Z",
                "level", "INFO",
                "service", "job-service",
                "message", "created job",
                "method", "POST",
                "path", "/api/jobs"));

        SearchResponse<Map> response = mock(SearchResponse.class, Answers.RETURNS_DEEP_STUBS);
        when(response.hits().hits()).thenReturn(List.of(hit));
        when(response.hits().total().value()).thenReturn(1L);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenReturn(response);

        LogSearchResponse result = logSearchService.searchLogs(LogSearchRequest.builder().build());

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getLogs()).hasSize(1);
        assertThat(result.getLogs().get(0).getService()).isEqualTo("job-service");
        assertThat(result.getLogs().get(0).getMethod()).isEqualTo("POST");
    }

    @SuppressWarnings("unchecked")
    @Test
    void getStatistics_returnsZeroStats_onIOException() throws IOException {
        init();
        when(elasticsearchClient.search(any(Function.class), eq(Void.class))).thenThrow(new IOException("es down"));

        LogStatistics result = logSearchService.getStatistics(24);

        assertThat(result.getTotalLogs()).isZero();
        assertThat(result.getByLevel()).isEmpty();
        assertThat(result.getByService()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getStatistics_aggregatesCounts_onSuccess() throws IOException {
        init();
        StringTermsBucket levelBucket = StringTermsBucket.of(b -> b.key(FieldValue.of("ERROR")).docCount(5));
        Aggregate byLevelAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(byLevelAgg.sterms().buckets().array()).thenReturn(List.of(levelBucket));

        StringTermsBucket serviceBucket = StringTermsBucket.of(b -> b.key(FieldValue.of("job-service")).docCount(8));
        Aggregate byServiceAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(byServiceAgg.sterms().buckets().array()).thenReturn(List.of(serviceBucket));

        Map<String, Aggregate> aggregations = Map.of(
                "by_level", byLevelAgg,
                "by_service", byServiceAgg);

        SearchResponse<Void> response = mock(SearchResponse.class, Answers.RETURNS_DEEP_STUBS);
        when(response.hits().total().value()).thenReturn(20L);
        when(response.aggregations()).thenReturn(aggregations);
        when(elasticsearchClient.search(any(Function.class), eq(Void.class))).thenReturn(response);

        LogStatistics result = logSearchService.getStatistics(24);

        assertThat(result.getTotalLogs()).isEqualTo(20L);
        assertThat(result.getByLevel()).containsEntry("ERROR", 5L);
        assertThat(result.getByService()).containsEntry("job-service", 8L);
        assertThat(result.getErrorRate()).isEqualTo(25.0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getLogsByTraceId_delegatesToSearchLogsWithTraceFilter() throws IOException {
        init();
        Hit<Map> hit = hitWith(Map.of("trace_id", "trace-1", "message", "x"));
        SearchResponse<Map> response = mock(SearchResponse.class, Answers.RETURNS_DEEP_STUBS);
        when(response.hits().hits()).thenReturn(List.of(hit));
        when(response.hits().total().value()).thenReturn(1L);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenReturn(response);

        var result = logSearchService.getLogsByTraceId("trace-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTraceId()).isEqualTo("trace-1");
    }

    @SuppressWarnings("unchecked")
    @Test
    void getRecentErrors_filtersOutNonUserActivity() throws IOException {
        init();
        Hit<Map> userHit = hitWith(Map.of(
                "username", "alice", "level", "ERROR", "method", "GET", "path", "/api/jobs", "message", "boom"));
        Hit<Map> systemHit = hitWith(Map.of(
                "username", "anonymous", "level", "ERROR", "method", "GET", "path", "/api/jobs", "message", "boom"));

        SearchResponse<Map> response = mock(SearchResponse.class, Answers.RETURNS_DEEP_STUBS);
        when(response.hits().hits()).thenReturn(List.of(userHit, systemHit));
        when(response.hits().total().value()).thenReturn(2L);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenReturn(response);

        var result = logSearchService.getRecentErrors(15);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("alice");
    }

}
