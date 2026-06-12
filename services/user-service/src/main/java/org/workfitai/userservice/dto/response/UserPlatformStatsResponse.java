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
        List<ExperienceBucket> candidateByExperience,
        List<CompanyHrCount> hrsByCompany
) {
    public record ExperienceBucket(String level, long count) {}

    public record CompanyHrCount(String companyNo, String companyName,
                                  long hrCount, long hrManagerCount) {}
}
