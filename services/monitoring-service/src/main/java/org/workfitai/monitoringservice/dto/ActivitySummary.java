package org.workfitai.monitoringservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Activity summary statistics for admin dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActivitySummary {
    /**
     * Unique active users in the time range
     */
    private int activeUsers;

    /**
     * Total requests/actions
     */
    private long totalActions;

    /**
     * Number of errors
     */
    private int errorCount;

    /**
     * Actions grouped by user (username -> count)
     */
    private Map<String, Integer> actionsByUser;

    /**
     * Actions grouped by service (service name -> count)
     */
    private Map<String, Integer> actionsByService;

    /**
     * Most common actions (action -> count)
     */
    private Map<String, Integer> topActions;
}
