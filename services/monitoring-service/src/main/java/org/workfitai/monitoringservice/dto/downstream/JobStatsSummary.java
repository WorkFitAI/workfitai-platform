package org.workfitai.monitoringservice.dto.downstream;

import java.util.Map;

/** Mirrors JobPlatformStatsResponse from job-service. */
public record JobStatsSummary(
        Map<String, Long> totalJobsByStatus,
        long totalCompanies,
        long jobsExpiringSoon,
        long totalJobViews,
        long pendingReports
) {}
