package org.workfitai.monitoringservice.dto.downstream;

import java.util.List;
import java.util.Map;

/** Mirrors HrmJobStatsResponse from job-service (company-scoped). */
public record HrmJobStatsSummary(
        long totalPublished,
        long totalDraft,
        long totalClosed,
        long expiringInWeek,
        long pendingReports,
        Map<String, Long> byEmploymentType,
        Map<String, Long> byJobCategory,
        Map<String, Long> byExperienceLevel,
        List<TopJobByViews> topJobsByViews
) {
    public record TopJobByViews(String jobId, String title, String companyName, long views) {}
}
