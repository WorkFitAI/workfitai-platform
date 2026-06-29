package org.workfitai.monitoringservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.workfitai.monitoringservice.dto.AuditEventResponse;
import org.workfitai.monitoringservice.dto.AuditSearchRequest;
import org.workfitai.monitoringservice.dto.AuditStatsResponse;
import org.workfitai.monitoringservice.dto.kafka.AuditEvent;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditSearchServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;
    @Mock
    private AuditPatternService auditPatternService;

    private AuditSearchService auditSearchService;

    private static final AuditEvent SAMPLE_EVENT = new AuditEvent(
            "evt-1", "auth-service", "alice", "ADMIN", "company-1",
            "USER", "user-9", "USER_BLOCKED", null, null,
            Instant.parse("2024-01-01T00:00:00Z"), true, null, "127.0.0.1");

    private void init() {
        auditSearchService = new AuditSearchService(elasticsearchClient, auditPatternService);
    }

    @Test
    void index_callsElasticsearchWithEventIdAsDocumentId() throws IOException {
        init();
        when(elasticsearchClient.index(any(IndexRequest.class))).thenReturn(mock(IndexResponse.class));

        auditSearchService.index(SAMPLE_EVENT);

        // no exception thrown means the indexing call succeeded
    }

    @Test
    void index_rethrowsRuntimeException_whenElasticsearchRejects() throws IOException {
        init();
        ElasticsearchException esException = mock(ElasticsearchException.class);
        when(esException.getMessage()).thenReturn("mapping conflict");
        when(elasticsearchClient.index(any(IndexRequest.class))).thenThrow(esException);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> auditSearchService.index(SAMPLE_EVENT));
    }

    @Test
    void index_rethrowsRuntimeException_onIOFailure() throws IOException {
        init();
        when(elasticsearchClient.index(any(IndexRequest.class))).thenThrow(new IOException("network error"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> auditSearchService.index(SAMPLE_EVENT));
    }

    @SuppressWarnings("unchecked")
    private SearchResponse<Map> deepStubSearchResponse() {
        return mock(SearchResponse.class, Answers.RETURNS_DEEP_STUBS);
    }

    @Test
    void searchAdmin_returnsEmptyPage_onElasticsearchIOException() throws IOException {
        init();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenThrow(new IOException("down"));

        Page<AuditEventResponse> result = auditSearchService.searchAdmin(emptyRequest(), PageRequest.of(0, 10));

        assertThat(result).isEmpty();
    }

    @Test
    void searchAdmin_returnsMappedResults_onSuccess() throws IOException {
        init();
        Hit<Map> hit = mock(Hit.class);
        Map<String, Object> source = Map.of(
                "eventId", "evt-1",
                "actorUsername", "alice",
                "actorRole", "ADMIN",
                "companyId", "company-1",
                "entityType", "USER",
                "entityId", "user-9",
                "action", "USER_BLOCKED",
                "occurredAt", "2024-01-01T00:00:00Z",
                "success", true);
        when(hit.source()).thenReturn(source);

        SearchResponse<Map> response = deepStubSearchResponse();
        when(response.hits().hits()).thenReturn(List.of(hit));
        when(response.hits().total().value()).thenReturn(1L);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenReturn(response);
        when(auditPatternService.resolveMessage("USER_BLOCKED")).thenReturn(Optional.of("{actor} blocked {target}"));

        Page<AuditEventResponse> result = auditSearchService.searchAdmin(emptyRequest(), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1L);
        AuditEventResponse first = result.getContent().get(0);
        assertThat(first.eventId()).isEqualTo("evt-1");
        assertThat(first.displayMessage()).isEqualTo("alice blocked user-9");
    }

    @Test
    void searchHrm_returnsEmptyPage_onElasticsearchIOException() throws IOException {
        init();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenThrow(new IOException("down"));

        Page<AuditEventResponse> result = auditSearchService.searchHrm("company-1", emptyRequest(), PageRequest.of(0, 10));

        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchHrm_returnsMappedResults_onSuccess() throws IOException {
        init();
        Hit<Map> hit = mock(Hit.class);
        Map<String, Object> source = Map.of(
                "eventId", "evt-2",
                "actorRole", "HR_MANAGER",
                "companyId", "company-1",
                "entityType", "USER",
                "action", "USER_BLOCKED",
                "occurredAt", "2024-01-01T00:00:00Z",
                "success", true);
        when(hit.source()).thenReturn(source);

        SearchResponse<Map> response = deepStubSearchResponse();
        when(response.hits().hits()).thenReturn(List.of(hit));
        when(response.hits().total().value()).thenReturn(1L);
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenReturn(response);
        when(auditPatternService.resolveMessage("USER_BLOCKED")).thenReturn(Optional.of("{actor} blocked {target}"));

        AuditSearchRequest fullRequest = new AuditSearchRequest(
                "auth-service", "alice", "ADMIN", "USER", null, "USER_BLOCKED", "ignored-company", true,
                Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-02T00:00:00Z"));

        Page<AuditEventResponse> result = auditSearchService.searchHrm("company-1", fullRequest, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1L);
        AuditEventResponse first = result.getContent().get(0);
        assertThat(first.eventId()).isEqualTo("evt-2");
        assertThat(first.actorUsername()).isNull();
        assertThat(first.displayMessage()).isEqualTo("System blocked USER");
    }

    @Test
    void getAuditStats_returnsZeroStats_onElasticsearchFailure() throws IOException {
        init();
        when(elasticsearchClient.search(any(java.util.function.Function.class), eq(Void.class)))
                .thenThrow(new IOException("down"));

        AuditStatsResponse result = auditSearchService.getAuditStats(null, null);

        assertThat(result.totalEvents()).isZero();
        assertThat(result.successRate()).isEqualTo(100.0);
    }

    @Test
    void getAuditStatsForCompany_returnsZeroStats_onElasticsearchFailure() throws IOException {
        init();
        when(elasticsearchClient.search(any(java.util.function.Function.class), eq(Void.class)))
                .thenThrow(new IOException("down"));

        AuditStatsResponse result = auditSearchService.getAuditStatsForCompany("company-1");

        assertThat(result.totalEvents()).isZero();
        assertThat(result.byService()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getAuditStats_aggregatesCounts_onSuccess() throws IOException {
        init();

        Aggregate failedAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(failedAgg.filter().docCount()).thenReturn(2L);

        Aggregate uniqueAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(uniqueAgg.cardinality().value()).thenReturn(3L);

        StringTermsBucket serviceBucket = StringTermsBucket.of(b -> b.key(FieldValue.of("auth-service")).docCount(5));
        Aggregate byServiceAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(byServiceAgg.sterms().buckets().array()).thenReturn(List.of(serviceBucket));

        StringTermsBucket actionBucket = StringTermsBucket.of(b -> b.key(FieldValue.of("USER_BLOCKED")).docCount(4));
        Aggregate byActionAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(byActionAgg.sterms().buckets().array()).thenReturn(List.of(actionBucket));

        StringTermsBucket actorBucket = StringTermsBucket.of(b -> b.key(FieldValue.of("alice")).docCount(4));
        Aggregate byActorAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(byActorAgg.sterms().buckets().array()).thenReturn(List.of(actorBucket));

        Map<String, Aggregate> aggregations = Map.of(
                "failedEvents", failedAgg,
                "uniqueActors", uniqueAgg,
                "byService", byServiceAgg,
                "byAction", byActionAgg,
                "byActor", byActorAgg);

        SearchResponse<Void> response = mock(SearchResponse.class, Answers.RETURNS_DEEP_STUBS);
        when(response.hits().total().value()).thenReturn(10L);
        when(response.aggregations()).thenReturn(aggregations);
        when(elasticsearchClient.search(any(java.util.function.Function.class), eq(Void.class))).thenReturn(response);

        AuditStatsResponse result = auditSearchService.getAuditStats(null, null);

        assertThat(result.totalEvents()).isEqualTo(10L);
        assertThat(result.failedEvents()).isEqualTo(2L);
        assertThat(result.uniqueActors()).isEqualTo(3L);
        assertThat(result.byService()).containsEntry("auth-service", 5L);
        assertThat(result.successRate()).isEqualTo(80.0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getAuditStatsForCompany_aggregatesCounts_onSuccess() throws IOException {
        init();

        Aggregate failedAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(failedAgg.filter().docCount()).thenReturn(1L);

        Aggregate uniqueAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(uniqueAgg.cardinality().value()).thenReturn(2L);

        StringTermsBucket serviceBucket = StringTermsBucket.of(b -> b.key(FieldValue.of("auth-service")).docCount(3));
        Aggregate byServiceAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(byServiceAgg.sterms().buckets().array()).thenReturn(List.of(serviceBucket));

        StringTermsBucket actionBucket = StringTermsBucket.of(b -> b.key(FieldValue.of("USER_BLOCKED")).docCount(2));
        Aggregate byActionAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(byActionAgg.sterms().buckets().array()).thenReturn(List.of(actionBucket));

        StringTermsBucket actorBucket = StringTermsBucket.of(b -> b.key(FieldValue.of("alice")).docCount(2));
        Aggregate byActorAgg = mock(Aggregate.class, Answers.RETURNS_DEEP_STUBS);
        when(byActorAgg.sterms().buckets().array()).thenReturn(List.of(actorBucket));

        Map<String, Aggregate> aggregations = Map.of(
                "failedEvents", failedAgg,
                "uniqueActors", uniqueAgg,
                "byService", byServiceAgg,
                "byAction", byActionAgg,
                "byActor", byActorAgg);

        SearchResponse<Void> response = mock(SearchResponse.class, Answers.RETURNS_DEEP_STUBS);
        when(response.hits().total().value()).thenReturn(4L);
        when(response.aggregations()).thenReturn(aggregations);
        when(elasticsearchClient.search(any(java.util.function.Function.class), eq(Void.class))).thenReturn(response);

        AuditStatsResponse result = auditSearchService.getAuditStatsForCompany("company-1");

        assertThat(result.totalEvents()).isEqualTo(4L);
        assertThat(result.failedEvents()).isEqualTo(1L);
        assertThat(result.uniqueActors()).isEqualTo(2L);
        assertThat(result.byService()).containsEntry("auth-service", 3L);
        assertThat(result.byAction()).containsEntry("USER_BLOCKED", 2L);
        assertThat(result.byActor()).containsEntry("alice", 2L);
    }

    @Test
    void getRecentErrorLogs_returnsEmptyList_onElasticsearchFailure() throws IOException {
        init();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenThrow(new IOException("down"));

        List<AuditEventResponse> result = auditSearchService.getRecentErrorLogs(10);

        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getRecentErrorLogs_returnsMappedResults_onSuccess() throws IOException {
        init();
        Hit<Map> hit = mock(Hit.class);
        Map<String, Object> source = Map.of(
                "eventId", "err-1",
                "actorRole", "ADMIN",
                "action", "LOGIN_FAILED",
                "occurredAt", "2024-01-01T00:00:00Z",
                "success", false);
        when(hit.source()).thenReturn(source);

        SearchResponse<Map> response = deepStubSearchResponse();
        when(response.hits().hits()).thenReturn(List.of(hit));
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenReturn(response);
        when(auditPatternService.resolveMessage("LOGIN_FAILED")).thenReturn(Optional.empty());

        List<AuditEventResponse> result = auditSearchService.getRecentErrorLogs(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).eventId()).isEqualTo("err-1");
    }

    @Test
    void getAuditStats_withTimeRange_doesNotThrow() throws IOException {
        init();
        when(elasticsearchClient.search(any(java.util.function.Function.class), eq(Void.class)))
                .thenThrow(new IOException("down"));

        AuditStatsResponse result = auditSearchService.getAuditStats(
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-31T00:00:00Z"));

        assertThat(result.totalEvents()).isZero();
    }

    @Test
    void searchAdmin_withCompanyIdFilter_returnsEmptyPage_onFailure() throws IOException {
        init();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Map.class))).thenThrow(new IOException("down"));

        AuditSearchRequest reqWithCompany = new AuditSearchRequest(
                null, null, null, null, null, null, "company-99", null, null, null);
        Page<AuditEventResponse> result = auditSearchService.searchAdmin(reqWithCompany, PageRequest.of(0, 10));

        assertThat(result).isEmpty();
    }

    private AuditSearchRequest emptyRequest() {
        return new AuditSearchRequest(null, null, null, null, null, null, null, null, null, null);
    }
}
