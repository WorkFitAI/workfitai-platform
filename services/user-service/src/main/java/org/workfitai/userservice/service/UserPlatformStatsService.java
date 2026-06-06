package org.workfitai.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.workfitai.userservice.dto.response.UserPlatformStatsResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.repository.CandidateRepository;
import org.workfitai.userservice.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserPlatformStatsService {

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;

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

        Map<String, Long> byEducation = candidateRepository.countByEducation();

        List<UserPlatformStatsResponse.ExperienceBucket> byExperience =
                candidateRepository.countByExperienceRange().stream()
                        .map(row -> new UserPlatformStatsResponse.ExperienceBucket(
                                (String) row[0], ((Number) row[1]).longValue()))
                        .toList();

        return new UserPlatformStatsResponse(
                byRole, totalActive, totalPending, totalBlocked, totalDeleted,
                byEducation, byExperience
        );
    }
}
