package org.workfitai.applicationservice.dto.response;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for job-specific application statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatsResponse {

    /**
     * Total applications for this job.
     */
    private long totalApplications;

    /**
     * Applications grouped by status.
     */
    private Map<String, Long> byStatus;

    /**
     * Conversion rates through the funnel.
     */
    private ConversionRates conversionRate;

    /**
     * Average time to review in days.
     */
    private double averageTimeToReviewDays;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversionRates {
        private double appliedToInterview;
        private double interviewToOffer;
        private double offerToHired;
    }
}
