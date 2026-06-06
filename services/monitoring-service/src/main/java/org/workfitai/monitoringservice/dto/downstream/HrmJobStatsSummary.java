package org.workfitai.monitoringservice.dto.downstream;

/** Mirrors HrmJobStatsResponse from job-service (company-scoped). */
public record HrmJobStatsSummary(
        long totalPublished,
        long totalDraft,
        long totalClosed,
        long expiringInWeek,
        long pendingReports
) {}
