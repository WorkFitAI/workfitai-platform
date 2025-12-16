package org.workfitai.applicationservice.dto.response;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for HR Manager dashboard statistics.
 *
 * Provides company-wide metrics including:
 * - Total applications and status breakdown
 * - Team performance (per HR user)
 * - Top jobs by applicant count
 * - Department breakdown (if available)
 *
 * Used for manager decision-making and workload distribution.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerStatsResponse {

    /**
     * Total number of applications for the company.
     */
    private Long totalApplications;

    /**
     * Applications grouped by status.
     * Map<status, count>
     * Example: {"APPLIED": 120, "REVIEWED": 45, "INTERVIEW": 30}
     */
    private Map<String, Long> byStatus;

    /**
     * Team performance metrics (per HR user).
     */
    private List<TeamPerformanceResponse> teamPerformance;

    /**
     * Top jobs by applicant count.
     */
    private List<JobApplicationCount> topJobs;

    /**
     * Applications by department (optional - requires department field).
     * Map<departmentId, count>
     */
    private Map<String, Long> byDepartment;

    /**
     * Inner class for team performance metrics.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TeamPerformanceResponse {
        /**
         * HR username.
         */
        private String hrUsername;

        /**
         * Number of applications assigned to this HR.
         */
        private Long assigned;

        /**
         * Number of applications reviewed (status changed from APPLIED).
         */
        private Long reviewed;

        /**
         * Average time to review in days.
         * Calculated as: avg(time between appliedAt and first status change).
         */
        private Double avgTimeToReviewDays;

        /**
         * Conversion rate (ACCEPTED / total reviewed).
         */
        private Double conversionRate;
    }

    /**
     * Inner class for job application counts.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class JobApplicationCount {
        /**
         * Job ID.
         */
        private String jobId;

        /**
         * Job title (from snapshot).
         */
        private String jobTitle;

        /**
         * Number of applicants.
         */
        private Long applicantCount;
    }
}
