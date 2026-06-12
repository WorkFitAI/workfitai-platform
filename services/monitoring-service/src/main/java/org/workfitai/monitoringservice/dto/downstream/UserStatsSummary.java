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
        List<ExperienceBucket> candidateByExperience,
        List<CompanyHrCount> hrsByCompany
) {
    public record ExperienceBucket(String level, long count) {}

    public record CompanyHrCount(String companyNo, String companyName,
                                  long hrCount, long hrManagerCount) {}
}
