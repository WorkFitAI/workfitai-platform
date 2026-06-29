package org.workfitai.monitoringservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.monitoringservice.dto.ActivitySummary;
import org.workfitai.monitoringservice.dto.UserActivityResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminActivityServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;
    @Mock
    private ActivityMessageFormatter messageFormatter;

    private AdminActivityService adminActivityService;

    private void init() {
        adminActivityService = new AdminActivityService(elasticsearchClient, messageFormatter);
        ReflectionTestUtils.setField(adminActivityService, "indexPattern", "workfitai-logs-*");
    }

    @SuppressWarnings("unchecked")
    @Test
    void getUserActivities_returnsEmptyResponse_onIOException() throws IOException {
        init();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenThrow(new IOException("es down"));

        UserActivityResponse result = adminActivityService.getUserActivities(null, null, 24, 0, 20);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getActivities()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getUserActivities_mapsHitsAndAggregations_onSuccess() throws IOException {
        init();
        when(messageFormatter.formatActivityMessage(any(), any(), any(), any(), any())).thenReturn("Đã tạo công việc");
        when(messageFormatter.formatRelativeTime(any())).thenReturn("5 phút trước");
        when(messageFormatter.getActivityIcon(any(), any())).thenReturn("➕");
        when(messageFormatter.formatByAction(any(), any())).thenReturn("Tạo công việc");

        Hit<Map> hit = mock(Hit.class);
        when(hit.id()).thenReturn("log-1");
        when(hit.source()).thenReturn(Map.of(
                "username", "alice",
                "action", "CREATE",
                "entity_type", "Job",
                "service", "job-service",
                "level", "INFO",
                "@timestamp", "2024-01-01T00:00:00Z"));

        StringTermsBucket userBucket = StringTermsBucket.of(b -> b.key(FieldValue.of("alice")).docCount(3));
        Aggregate userAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(userAgg.isSterms()).thenReturn(true);
        when(userAgg.sterms().buckets().array()).thenReturn(List.of(userBucket));

        StringTermsBucket serviceBucket = StringTermsBucket.of(b -> b.key(FieldValue.of("job-service")).docCount(3));
        Aggregate serviceAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(serviceAgg.isSterms()).thenReturn(true);
        when(serviceAgg.sterms().buckets().array()).thenReturn(List.of(serviceBucket));

        StringTermsBucket actionBucket = StringTermsBucket.of(b -> b.key(FieldValue.of("CREATE")).docCount(3));
        Aggregate actionAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(actionAgg.isSterms()).thenReturn(true);
        when(actionAgg.sterms().buckets().array()).thenReturn(List.of(actionBucket));

        Aggregate errorAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(errorAgg.isFilter()).thenReturn(true);
        when(errorAgg.filter().docCount()).thenReturn(0L);

        Map<String, Aggregate> aggregations = Map.of(
                "by_user", userAgg,
                "by_service", serviceAgg,
                "by_action", actionAgg,
                "error_count", errorAgg);

        SearchResponse<Map> response = mock(SearchResponse.class, Answers.RETURNS_DEEP_STUBS);
        when(response.hits().hits()).thenReturn(List.of(hit));
        when(response.hits().total().value()).thenReturn(1L);
        when(response.aggregations()).thenReturn(aggregations);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenReturn(response);

        UserActivityResponse result = adminActivityService.getUserActivities("alice", null, 24, 0, 20);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getActivities()).hasSize(1);
        assertThat(result.getActivities().get(0).getUsername()).isEqualTo("alice");
        ActivitySummary summary = result.getSummary();
        assertThat(summary.getActiveUsers()).isEqualTo(1);
        assertThat(summary.getActionsByUser()).containsEntry("alice", 3);
    }

    @Test
    void getActivitySummary_delegatesToGetUserActivities() throws IOException {
        init();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenThrow(new IOException("es down"));

        ActivitySummary summary = adminActivityService.getActivitySummary(24);

        assertThat(summary).isNull();
    }
}
