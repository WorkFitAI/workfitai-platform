package org.workfitai.userservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.workfitai.userservice.dto.elasticsearch.UserSearchRequest;
import org.workfitai.userservice.dto.elasticsearch.UserSearchResponse;
import org.workfitai.userservice.dto.kafka.UserDocument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for searching users in Elasticsearch with advanced query
 * capabilities.
 * Provides fast full-text search, filtering, and aggregations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private static final String INDEX_NAME = "users-index";

    /**
     * Search users with advanced filtering and aggregations
     */
    public UserSearchResponse searchUsers(UserSearchRequest request) {
        try {
            SearchRequest searchRequest = buildSearchRequest(request);
            SearchResponse<UserDocument> response = elasticsearchClient.search(searchRequest, UserDocument.class);

            return buildSearchResponse(response, request);
        } catch (Exception e) {
            log.error("Error searching users in Elasticsearch: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search users", e);
        }
    }

    private SearchRequest buildSearchRequest(UserSearchRequest request) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // Add search query
        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            Query multiMatchQuery = Query.of(q -> q
                    .multiMatch(m -> m
                            .query(request.getQuery())
                            .fields("username^3", "email^2", "fullName^2", "phoneNumber")
                            .fuzziness("AUTO")));
            boolQuery.must(multiMatchQuery);
        }

        // Add filters
        addFilters(boolQuery, request);

        // Build search request
        SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                .index(INDEX_NAME)
                .query(boolQuery.build()._toQuery())
                .from(request.getFrom())
                .size(request.getSize())
                .sort(s -> s.field(f -> f
                        .field(request.getSortField())
                        .order("desc".equalsIgnoreCase(request.getSortOrder()) ? SortOrder.Desc : SortOrder.Asc)));

        // Add aggregations if requested
        if (request.isIncludeAggregations()) {
            searchBuilder
                    .aggregations("roles", Aggregation.of(a -> a
                            .terms(t -> t.field("role.keyword"))))
                    .aggregations("statuses", Aggregation.of(a -> a
                            .terms(t -> t.field("status.keyword"))));
        }

        // Add highlighting
        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            searchBuilder.highlight(h -> h
                    .fields("username", f -> f)
                    .fields("email", f -> f)
                    .fields("fullName", f -> f));
        }

        return searchBuilder.build();
    }

    private void addFilters(BoolQuery.Builder boolQuery, UserSearchRequest request) {
        // Role filter
        if (request.getRole() != null && !request.getRole().isBlank()) {
            boolQuery.filter(Query.of(q -> q
                    .term(t -> t
                            .field("role.keyword")
                            .value(FieldValue.of(request.getRole())))));
        }

        // Status filter
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            boolQuery.filter(Query.of(q -> q
                    .term(t -> t
                            .field("status.keyword")
                            .value(FieldValue.of(request.getStatus())))));
        }

        // Blocked filter
        if (request.getBlocked() != null) {
            boolQuery.filter(Query.of(q -> q
                    .term(t -> t
                            .field("blocked")
                            .value(FieldValue.of(request.getBlocked())))));
        }

        // Deleted filter (default: exclude deleted users)
        boolQuery.filter(Query.of(q -> q
                .term(t -> t
                        .field("deleted")
                        .value(FieldValue.of(request.getIncludeDeleted() != null && request.getIncludeDeleted())))));

        // Date range filters
        if (request.getCreatedAfter() != null || request.getCreatedBefore() != null) {
            boolQuery.filter(Query.of(q -> q
                    .range(r -> {
                        var rangeBuilder = r.field("createdDate");
                        if (request.getCreatedAfter() != null) {
                            rangeBuilder.gte(co.elastic.clients.json.JsonData.of(request.getCreatedAfter().toString()));
                        }
                        if (request.getCreatedBefore() != null) {
                            rangeBuilder
                                    .lte(co.elastic.clients.json.JsonData.of(request.getCreatedBefore().toString()));
                        }
                        return rangeBuilder;
                    })));
        }
    }

    private UserSearchResponse buildSearchResponse(SearchResponse<UserDocument> response, UserSearchRequest request) {
        List<UserSearchResponse.UserSearchHit> hits = response.hits().hits().stream()
                .map(this::mapToSearchHit)
                .collect(Collectors.toList());

        UserSearchResponse result = UserSearchResponse.builder()
                .hits(hits)
                .totalHits(response.hits().total() != null ? response.hits().total().value() : 0)
                .from(request.getFrom())
                .size(request.getSize())
                .build();

        // Add aggregations if available
        if (request.isIncludeAggregations() && response.aggregations() != null) {
            Map<String, Long> roleAggregations = new HashMap<>();
            Map<String, Long> statusAggregations = new HashMap<>();

            if (response.aggregations().get("roles") != null) {
                StringTermsAggregate rolesAgg = response.aggregations().get("roles").sterms();
                for (StringTermsBucket bucket : rolesAgg.buckets().array()) {
                    roleAggregations.put(bucket.key().stringValue(), bucket.docCount());
                }
            }

            if (response.aggregations().get("statuses") != null) {
                StringTermsAggregate statusesAgg = response.aggregations().get("statuses").sterms();
                for (StringTermsBucket bucket : statusesAgg.buckets().array()) {
                    statusAggregations.put(bucket.key().stringValue(), bucket.docCount());
                }
            }

            result.setRoleAggregations(roleAggregations);
            result.setStatusAggregations(statusAggregations);
        }

        return result;
    }

    private UserSearchResponse.UserSearchHit mapToSearchHit(Hit<UserDocument> hit) {
        UserDocument doc = hit.source();
        Map<String, List<String>> highlights = new HashMap<>();

        if (hit.highlight() != null) {
            hit.highlight().forEach((field, values) -> highlights.put(field, new ArrayList<>(values)));
        }

        return UserSearchResponse.UserSearchHit.builder()
                .userId(doc.getUserId())
                .username(doc.getUsername())
                .email(doc.getEmail())
                .fullName(doc.getFullName())
                .phoneNumber(doc.getPhoneNumber())
                .role(doc.getRole())
                .status(doc.getStatus())
                .blocked(doc.isBlocked())
                .deleted(doc.isDeleted())
                .createdAt(doc.getCreatedDate())
                .updatedAt(doc.getLastModifiedDate())
                .score(hit.score() != null ? hit.score() : 0.0)
                .highlights(highlights)
                .build();
    }
}
