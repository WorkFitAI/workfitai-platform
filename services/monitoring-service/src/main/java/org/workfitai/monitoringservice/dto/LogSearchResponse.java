package org.workfitai.monitoringservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for log search results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogSearchResponse {

    /**
     * Total number of matching logs
     */
    private long total;

    /**
     * Current page number
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
     * Log entries
     */
    private List<LogEntry> logs;

    /**
     * Aggregations/statistics
     */
    private Map<String, Object> aggregations;
}
