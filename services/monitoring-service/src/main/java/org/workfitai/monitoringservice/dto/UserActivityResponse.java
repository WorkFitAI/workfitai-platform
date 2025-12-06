package org.workfitai.monitoringservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for user activity search results.
 * Optimized for admin dashboard display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserActivityResponse {

    /**
     * Total number of matching activities
     */
    private long total;

    /**
     * Current page number (0-indexed)
     */
    private int page;

    /**
     * Page size
     */
    private int size;

    /**
     * Total pages
     */
    private int totalPages;

    /**
     * Activity entries
     */
    private List<UserActivityEntry> activities;

    /**
     * Summary statistics
     */
    private ActivitySummary summary;

    /**
     * Activity summary for dashboard widgets
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivitySummary {
        /**
         * Unique active users in the time range
         */
        private long activeUsers;

        /**
         * Total requests/actions
         */
        private long totalActions;

        /**
         * Number of errors
         */
        private long errorCount;

        /**
         * Actions grouped by user
         */
        private Map<String, Long> actionsByUser;

        /**
         * Actions grouped by service
         */
        private Map<String, Long> actionsByService;

        /**
         * Most common actions
         */
        private Map<String, Long> topActions;
    }
}
