package org.workfitai.applicationservice.dto.response;

import java.util.List;
import java.util.Map;

/**
 * Response for system-wide admin statistics
 */
public record SystemStatsResponse(
    PlatformTotals platformTotals,
    List<CompanyStats> byCompany,
    Map<String, Long> byStatus,
    GrowthMetrics growthMetrics,
    List<TopJob> topJobs,
    double avgTimeToHire,
    Map<String, Double> platformConversionRates,
    List<DailyCount> volumeTrend
) {
    public record PlatformTotals(
        long totalApplications,
        long totalDeleted,
        long totalCompanies,
        long totalJobs
    ) {}

    public record CompanyStats(
        String companyId,
        String companyName,
        long applications,
        long activeJobs,
        double avgTimeToHire
    ) {}

    public record GrowthMetrics(
        long last7Days,
        long last30Days,
        double monthOverMonth,
        double yearOverYear
    ) {}

    public record TopJob(
        String jobId,
        String jobTitle,
        String companyName,
        long applications,
        long hires
    ) {}

    public record DailyCount(String date, long count) {}
}
