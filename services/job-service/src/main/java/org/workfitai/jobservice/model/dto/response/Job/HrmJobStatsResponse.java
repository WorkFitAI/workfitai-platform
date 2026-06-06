package org.workfitai.jobservice.model.dto.response.Job;

public record HrmJobStatsResponse(
        long totalPublished,
        long totalDraft,
        long totalClosed,
        long expiringInWeek,
        long pendingReports
) {}
