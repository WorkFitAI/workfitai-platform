package org.workfitai.userservice.dto.elasticsearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for Elasticsearch user search.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSearchResponse {

    private List<UserSearchHit> hits;
    private long totalHits;
    private int from;
    private int size;

    // Aggregations: role -> count, status -> count
    private Map<String, Long> roleAggregations;
    private Map<String, Long> statusAggregations;

    /**
     * Individual user search result with highlights
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserSearchHit {
        private String userId;
        private String username;
        private String fullName;
        private String email;
        private String phoneNumber;
        private String avatarUrl;
        private String role; // Store as String instead of enum
        private String status; // Store as String instead of enum
        private boolean blocked;
        private boolean deleted;
        private Instant createdAt;
        private Instant updatedAt;

        // Company information (for HR/HR_MANAGER users)
        private String companyNo;
        private String companyName;

        /**
         * Elasticsearch relevance score
         */
        private Double score;

        /**
         * Highlighted matched text
         * Key: field name (e.g., "fullName")
         * Value: highlighted text (e.g., "<em>John</em> Doe")
         */
        private Map<String, List<String>> highlights;
    }
}
