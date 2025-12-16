package org.workfitai.applicationservice.dto.response;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for HR dashboard statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {

    /**
     * Total number of applications.
     */
    private long totalApplications;

    /**
     * Applications grouped by status.
     */
    private Map<String, Long> byStatus;

    /**
     * Recent applications (last 10).
     */
    private List<ApplicationResponse> recentApplications;

    /**
     * Applications grouped by job.
     */
    private List<JobApplicationCount> byJob;

    /**
     * Weekly application trend.
     */
    private List<WeeklyCount> weeklyTrend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobApplicationCount {
        private String jobId;
        private String jobTitle;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyCount {
        private String week; // Format: "2024-W01"
        private long count;
    }
}
