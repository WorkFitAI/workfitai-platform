package org.workfitai.jobservice.model.dto.response.Job;

import java.util.List;
import java.util.Map;

public record JobPlatformStatsResponse(
        Map<String, Long> totalJobsByStatus,
        long totalCompanies,
        long jobsExpiringSoon,
        long totalJobViews,
        long pendingReports,
        Map<String, Long> byEmploymentType,
        Map<String, Long> byExperienceLevel,
        List<TopJobByViews> topJobsByViews
) {
    public record TopJobByViews(String jobId, String title, String companyName, long views) {}
}
