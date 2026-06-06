package org.workfitai.userservice.dto.response;

import java.util.List;
import java.util.Map;

public record UserPlatformStatsResponse(
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
