package org.workfitai.userservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.userservice.dto.response.UserPlatformStatsResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.repository.CandidateRepository;
import org.workfitai.userservice.repository.HRRepository;
import org.workfitai.userservice.repository.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPlatformStatsServiceTest {

    @Mock UserRepository userRepository;
    @Mock CandidateRepository candidateRepository;
    @Mock HRRepository hrRepository;

    @InjectMocks
    UserPlatformStatsService service;

    @Test
    void getStats_aggregatesAllRepos() {
        when(userRepository.countByRoleRaw()).thenReturn(List.of(
                new Object[]{EUserRole.CANDIDATE, 100L},
                new Object[]{EUserRole.HR, 20L},
                new Object[]{EUserRole.HR_MANAGER, 5L}
        ));
        when(userRepository.countByUserStatus(EUserStatus.ACTIVE)).thenReturn(80L);
        when(userRepository.countByUserStatus(EUserStatus.PENDING)).thenReturn(15L);
        when(userRepository.countBlocked()).thenReturn(3L);
        when(userRepository.countByDeletedAtIsNotNull()).thenReturn(7L);

        when(candidateRepository.countByEducation()).thenReturn(List.of(
                new Object[]{"Bachelor", 50L},
                new Object[]{"Master", 30L}
        ));
        when(candidateRepository.countByExperienceRange()).thenReturn(List.of(
                new Object[]{"Junior", 40L},
                new Object[]{"Mid-Level", 35L},
                new Object[]{"Senior", 25L}
        ));
        when(hrRepository.countActiveHrByCompanyAndRole(EUserStatus.ACTIVE)).thenReturn(List.of(
                new Object[]{"TAX001", "Company A", EUserRole.HR, 5L},
                new Object[]{"TAX001", "Company A", EUserRole.HR_MANAGER, 1L},
                new Object[]{"TAX002", "Company B", EUserRole.HR, 3L}
        ));

        UserPlatformStatsResponse stats = service.getStats();

        assertThat(stats.totalByRole()).containsKey("CANDIDATE");
        assertThat(stats.totalByRole().get("CANDIDATE")).isEqualTo(100L);
        assertThat(stats.totalActive()).isEqualTo(80L);
        assertThat(stats.totalPending()).isEqualTo(15L);
        assertThat(stats.totalBlocked()).isEqualTo(3L);
        assertThat(stats.totalDeleted()).isEqualTo(7L);
        assertThat(stats.candidateByEducation()).containsKey("Bachelor");
        assertThat(stats.candidateByExperience()).hasSize(3);
        assertThat(stats.hrsByCompany()).hasSize(2);

        // Company A should have 5 HR + 1 HR Manager = 6 total → listed first
        UserPlatformStatsResponse.CompanyHrCount companyA = stats.hrsByCompany().get(0);
        assertThat(companyA.companyNo()).isEqualTo("TAX001");
        assertThat(companyA.hrCount()).isEqualTo(5L);
        assertThat(companyA.hrManagerCount()).isEqualTo(1L);
    }

    @Test
    void getStats_emptyRepos_returnsZeroes() {
        when(userRepository.countByRoleRaw()).thenReturn(List.of());
        when(userRepository.countByUserStatus(any())).thenReturn(0L);
        when(userRepository.countBlocked()).thenReturn(0L);
        when(userRepository.countByDeletedAtIsNotNull()).thenReturn(0L);
        when(candidateRepository.countByEducation()).thenReturn(List.of());
        when(candidateRepository.countByExperienceRange()).thenReturn(List.of());
        when(hrRepository.countActiveHrByCompanyAndRole(any())).thenReturn(List.of());

        UserPlatformStatsResponse stats = service.getStats();

        assertThat(stats.totalByRole()).isEmpty();
        assertThat(stats.totalActive()).isEqualTo(0L);
        assertThat(stats.hrsByCompany()).isEmpty();
    }
}
