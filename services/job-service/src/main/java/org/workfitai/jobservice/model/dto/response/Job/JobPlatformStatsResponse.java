package org.workfitai.jobservice.model.dto.response.Job;

import java.util.Map;

public record JobPlatformStatsResponse(
        Map<String, Long> totalJobsByStatus,
        long totalCompanies,
        long jobsExpiringSoon,
        long totalJobViews,
        long pendingReports
) {}
