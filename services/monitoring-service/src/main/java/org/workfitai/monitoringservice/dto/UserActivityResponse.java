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
}
