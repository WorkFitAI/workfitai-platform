package org.workfitai.monitoringservice.dto.downstream;

import java.util.List;
import java.util.Map;

/** Mirrors ManagerStatsResponse from application-service (only fields consumed by dashboard). */
public record ManagerStatsSummary(
        Long totalApplications,
        Map<String, Long> byStatus,
        List<JobCount> topJobs,
        Long stuckApplicationsCount,
        Map<String, Double> conversionRates,
        List<DailyCount> volumeTrend,
        Long offerAcceptedCount,
        Long offerRejectedCount
) {
    public record JobCount(String jobId, String jobTitle, Long applicantCount) {}

    public record DailyCount(String date, long count) {}
}
