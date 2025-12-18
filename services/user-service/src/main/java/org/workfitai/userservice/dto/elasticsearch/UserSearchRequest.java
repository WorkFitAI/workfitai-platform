package org.workfitai.userservice.dto.elasticsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request DTO for Elasticsearch user search.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSearchRequest {

    /**
     * Full-text search query (searches username, fullName, email)
     */
    private String query;

    /**
     * Filter by single role
     */
    private String role;

    /**
     * Filter by single status
     */
    private String status;

    /**
     * Filter by blocked status
     */
    private Boolean blocked;

    /**
     * Filter by company ID (for HR filtering)
     */
    private String companyId;

    /**
     * Filter by company name (for HR filtering)
     */
    private String companyName;

    /**
     * Include deleted users in results
     */
    private Boolean includeDeleted;

    /**
     * Filter by created after date
     */
    private Instant createdAfter;

    /**
     * Filter by created before date
     */
    private Instant createdBefore;

    /**
     * Sort field (default: createdDate)
     */
    @Builder.Default
    private String sortField = "createdDate";

    /**
     * Sort order (asc/desc, default: desc)
     */
    @Builder.Default
    private String sortOrder = "desc";

    /**
     * Offset for pagination
     */
    @Builder.Default
    private int from = 0;

    /**
     * Page size (default: 20, max: 100)
     */
    @Builder.Default
    private int size = 20;

    /**
     * Include aggregations in response
     */
    @Builder.Default
    private boolean includeAggregations = false;
}
