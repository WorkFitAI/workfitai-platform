package org.workfitai.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.workfitai.userservice.dto.response.UserPlatformStatsResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.repository.CandidateRepository;
import org.workfitai.userservice.repository.HRRepository;
import org.workfitai.userservice.repository.UserRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserPlatformStatsService {

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final HRRepository hrRepository;

    public UserPlatformStatsResponse getStats() {
        Map<String, Long> byRole = userRepository.countByRoleRaw().stream()
                .collect(Collectors.toMap(
                        row -> ((EUserRole) row[0]).name(),
                        row -> (Long) row[1]
                ));

        long totalActive  = userRepository.countByUserStatus(EUserStatus.ACTIVE);
        long totalPending = userRepository.countByUserStatus(EUserStatus.PENDING);
        long totalBlocked = userRepository.countBlocked();
        long totalDeleted = userRepository.countByDeletedAtIsNotNull();

        Map<String, Long> byEducation = candidateRepository.countByEducation().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));

        List<UserPlatformStatsResponse.ExperienceBucket> byExperience =
                candidateRepository.countByExperienceRange().stream()
                        .map(row -> new UserPlatformStatsResponse.ExperienceBucket(
                                (String) row[0], ((Number) row[1]).longValue()))
                        .toList();

        List<UserPlatformStatsResponse.CompanyHrCount> hrsByCompany = buildHrsByCompany();

        return new UserPlatformStatsResponse(
                byRole, totalActive, totalPending, totalBlocked, totalDeleted,
                byEducation, byExperience, hrsByCompany
        );
    }

    private List<UserPlatformStatsResponse.CompanyHrCount> buildHrsByCompany() {
        List<Object[]> rows = hrRepository.countActiveHrByCompanyAndRole(EUserStatus.ACTIVE);
        // rows: [companyNo, companyName, EUserRole, count]
        Map<String, long[]> byCompany = new LinkedHashMap<>();
        Map<String, String> companyNames = new HashMap<>();

        for (Object[] row : rows) {
            String companyNo   = (String) row[0];
            String companyName = (String) row[1];
            EUserRole role     = (EUserRole) row[2];
            long count         = ((Number) row[3]).longValue();

            companyNames.put(companyNo, companyName);
            long[] counts = byCompany.computeIfAbsent(companyNo, k -> new long[2]);
            if (role == EUserRole.HR)         counts[0] += count;
            if (role == EUserRole.HR_MANAGER) counts[1] += count;
        }

        return byCompany.entrySet().stream()
                .map(e -> new UserPlatformStatsResponse.CompanyHrCount(
                        e.getKey(), companyNames.get(e.getKey()), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(c -> -(c.hrCount() + c.hrManagerCount())))
                .toList();
    }
}
