package org.workfitai.monitoringservice.dto.downstream;

import java.util.List;
import java.util.Map;

/** Mirrors UserPlatformStatsResponse from user-service. */
public record UserStatsSummary(
        Map<String, Long> totalByRole,
        long totalActive,
        long totalPending,
        long totalBlocked,
        long totalDeleted,
        Map<String, Long> candidateByEducation,
        List<ExperienceBucket> candidateByExperience
) {
    public record ExperienceBucket(String level, long count) {}
}
