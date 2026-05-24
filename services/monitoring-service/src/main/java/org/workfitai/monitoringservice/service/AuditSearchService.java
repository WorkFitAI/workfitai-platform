package org.workfitai.monitoringservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.workfitai.monitoringservice.dto.AuditEventResponse;
import org.workfitai.monitoringservice.dto.AuditSearchRequest;
import org.workfitai.monitoringservice.dto.kafka.AuditEvent;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch service for audit log indexing and search.
 *
 * Index pattern: workfitai-audit-{YYYY.MM.dd} (daily rolling, separate from ops logs).
 * Indexing uses eventId as the document _id for idempotent re-indexing on consumer replay.
 *
 * Admin search: unrestricted — all services, all companies.
 * HRM search: companyId filter always appended from the caller's JWT claim.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditSearchService {

    private final ElasticsearchClient elasticsearchClient;

    private static final String INDEX_PREFIX = "workfitai-audit-";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    // ─── Indexing ────────────────────────────────────────────────────────────────

    /**
     * Indexes an AuditEvent into the daily rolling index.
     * Uses eventId as document _id so replaying the consumer is safe (idempotent).
     */
    public void index(AuditEvent event) {
        String indexName = INDEX_PREFIX + ZonedDateTime.now(ZoneOffset.UTC).format(DATE_FORMAT);
        try {
            elasticsearchClient.index(IndexRequest.of(req -> req
                    .index(indexName)
                    .id(event.eventId())
                    .document(toDocument(event))));
            log.debug("[AUDIT-INDEX] {} {} {} → {}", event.sourceService(), event.action(),
                    event.entityId(), indexName);
        } catch (IOException e) {
            log.error("[AUDIT-INDEX] Failed to index event {} into {}: {}", event.eventId(), indexName, e.getMessage());
        }
    }

    // ─── Search: ADMIN (unrestricted) ────────────────────────────────────────────

    /**
     * Full unrestricted audit search for ADMIN role.
     * Applies all optional filters from the request, with no forced companyId.
     */
    public Page<AuditEventResponse> searchAdmin(AuditSearchRequest req, Pageable pageable) {
        return search(req, null, false, pageable);
    }

    // ─── Search: HRM (company-scoped) ────────────────────────────────────────────

    /**
     * Company-scoped audit search for HR_MANAGER role.
     * Always appends a companyId MUST filter; ignores any companyId in the request itself.
     */
    public Page<AuditEventResponse> searchHrm(String companyId, AuditSearchRequest req, Pageable pageable) {
        return search(req, companyId, true, pageable);
    }

    // ─── Internal search ─────────────────────────────────────────────────────────

    private Page<AuditEventResponse> search(AuditSearchRequest req,
                                             String forcedCompanyId,
                                             boolean enforceCompanyScope,
                                             Pageable pageable) {
        List<Query> filters = new ArrayList<>();

        // Optional filters from request
        addTermFilter(filters, "sourceService", req.sourceService());
        addTermFilter(filters, "actorUsername", req.actorUsername());
        addTermFilter(filters, "entityType",    req.entityType());
        addTermFilter(filters, "entityId",      req.entityId());
        addTermFilter(filters, "action",        req.action());

        // ADMIN may filter by companyId optionally; HRM always uses forced value
        if (enforceCompanyScope && forcedCompanyId != null) {
            addTermFilter(filters, "companyId", forcedCompanyId);
        } else if (!enforceCompanyScope && req.companyId() != null) {
            addTermFilter(filters, "companyId", req.companyId());
        }

        // Time range filter
        if (req.from() != null || req.to() != null) {
            filters.add(Query.of(q -> q.range(r -> {
                r.field("occurredAt");
                if (req.from() != null) r.gte(co.elastic.clients.json.JsonData.of(req.from().toString()));
                if (req.to()   != null) r.lte(co.elastic.clients.json.JsonData.of(req.to().toString()));
                return r;
            })));
        }

        BoolQuery boolQuery = BoolQuery.of(b -> b.filter(filters));

        int from = (int) pageable.getOffset();
        int size  = pageable.getPageSize();

        try {
            SearchResponse<Map> response = elasticsearchClient.search(
                    SearchRequest.of(s -> s
                            .index(INDEX_PREFIX + "*")
                            .query(q -> q.bool(boolQuery))
                            .sort(sort -> sort.field(f -> f.field("occurredAt").order(SortOrder.Desc)))
                            .from(from)
                            .size(size)),
                    Map.class);

            List<AuditEventResponse> items = response.hits().hits().stream()
                    .map(Hit::source)
                    .map(this::toResponse)
                    .toList();

            long total = response.hits().total() != null ? response.hits().total().value() : 0L;
            return new PageImpl<>(items, pageable, total);

        } catch (IOException e) {
            log.error("[AUDIT-SEARCH] Elasticsearch query failed: {}", e.getMessage());
            return Page.empty(pageable);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private void addTermFilter(List<Query> filters, String field, String value) {
        if (value != null && !value.isBlank()) {
            filters.add(Query.of(q -> q.term(t -> t.field(field).value(value))));
        }
    }

    /** Converts AuditEvent record to a plain Map for Elasticsearch indexing. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toDocument(AuditEvent event) {
        java.util.LinkedHashMap<String, Object> doc = new java.util.LinkedHashMap<>();
        doc.put("eventId",       event.eventId());
        doc.put("sourceService", event.sourceService());
        doc.put("actorUsername", event.actorUsername());
        doc.put("actorRole",     event.actorRole());
        doc.put("companyId",     event.companyId());
        doc.put("entityType",    event.entityType());
        doc.put("entityId",      event.entityId());
        doc.put("action",        event.action());
        doc.put("before",        event.before());
        doc.put("after",         event.after());
        doc.put("occurredAt",    event.occurredAt() != null ? event.occurredAt().toString() : null);
        return doc;
    }

    /** Maps a raw Elasticsearch hit (Map) back to an AuditEventResponse. */
    @SuppressWarnings("unchecked")
    private AuditEventResponse toResponse(Map<?, ?> source) {
        if (source == null) return null;

        String occurredAtStr = (String) source.get("occurredAt");
        java.time.Instant occurredAt = occurredAtStr != null
                ? java.time.Instant.parse(occurredAtStr) : null;

        return new AuditEventResponse(
                (String) source.get("eventId"),
                (String) source.get("sourceService"),
                (String) source.get("actorUsername"),
                (String) source.get("actorRole"),
                (String) source.get("companyId"),
                (String) source.get("entityType"),
                (String) source.get("entityId"),
                (String) source.get("action"),
                (Map<String, Object>) source.get("before"),
                (Map<String, Object>) source.get("after"),
                occurredAt
        );
    }
}
