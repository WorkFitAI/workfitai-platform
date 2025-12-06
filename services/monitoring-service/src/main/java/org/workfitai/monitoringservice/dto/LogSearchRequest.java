package org.workfitai.monitoringservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Request DTO for log search queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogSearchRequest {

    /**
     * Free text search query
     */
    private String query;

    /**
     * Filter by service name (e.g., "auth-service", "user-service")
     */
    private String service;

    /**
     * Filter by log levels (e.g., ["ERROR", "WARN"])
     */
    private List<String> levels;

    /**
     * Start time for time range filter
     */
    private Instant from;

    /**
     * End time for time range filter
     */
    private Instant to;

    /**
     * Filter by trace ID for distributed tracing
     */
    private String traceId;

    /**
     * Filter by user ID
     */
    private String userId;

    /**
     * Filter by username
     */
    private String username;

    /**
     * Filter by request ID
     */
    private String requestId;

    /**
     * Page number (0-indexed)
     */
    @Builder.Default
    private int page = 0;

    /**
     * Page size
     */
    @Builder.Default
    private int size = 50;

    /**
     * Sort order: "asc" or "desc"
     */
    @Builder.Default
    private String sortOrder = "desc";
}
