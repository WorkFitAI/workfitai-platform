package org.workfitai.monitoringservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for log statistics and aggregations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogStatistics {

    /**
     * Total log count
     */
    private long totalLogs;

    /**
     * Log count by level (ERROR, WARN, INFO, DEBUG)
     */
    private Map<String, Long> byLevel;

    /**
     * Log count by service
     */
    private Map<String, Long> byService;

    /**
     * Error rate percentage
     */
    private double errorRate;

    /**
     * Time range in hours
     */
    private int timeRangeHours;

    /**
     * Average logs per minute
     */
    private double logsPerMinute;
}
