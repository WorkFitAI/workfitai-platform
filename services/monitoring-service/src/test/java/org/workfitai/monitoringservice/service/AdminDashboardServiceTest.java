package org.workfitai.monitoringservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.monitoringservice.client.ApplicationServiceClient;
import org.workfitai.monitoringservice.client.JobServiceClient;
import org.workfitai.monitoringservice.client.JobStatsServiceClient;
import org.workfitai.monitoringservice.client.UserServiceClient;
import org.workfitai.monitoringservice.dto.AdminDashboardResponse;
import org.workfitai.monitoringservice.dto.AuditStatsResponse;
import org.workfitai.monitoringservice.dto.downstream.DownstreamApiResponse;
import org.workfitai.monitoringservice.dto.downstream.JobStatsSummary;
import org.workfitai.monitoringservice.dto.downstream.SystemStatsSummary;
import org.workfitai.monitoringservice.dto.downstream.TopSkillItem;
import org.workfitai.monitoringservice.dto.downstream.UserStatsSummary;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private ApplicationServiceClient applicationServiceClient;
    @Mock
    private JobServiceClient jobServiceClient;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private JobStatsServiceClient jobStatsServiceClient;
    @Mock
    private AuditSearchService auditSearchService;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    private final AuditStatsResponse emptyAuditStats = new AuditStatsResponse(0, 0, 100.0, 0, Map.of(), Map.of(), Map.of());

    @BeforeEach
    void setUp() {
        lenient().when(auditSearchService.getAuditStats(null, null)).thenReturn(emptyAuditStats);
    }

    @Test
    void getDashboard_aggregatesAllSources_onSuccess() {
        SystemStatsSummary systemStats = new SystemStatsSummary(null, List.of(), Map.of(), null, List.of(), 0, Map.of(), List.of(), 0, 0);
        UserStatsSummary userStats = new UserStatsSummary(Map.of(), 10, 0, 0, 0, Map.of(), List.of(), List.of());
        JobStatsSummary jobStats = new JobStatsSummary(Map.of(), 5, 0, 0, 0, Map.of(), Map.of(), Map.of(), List.of());
        List<TopSkillItem> topSkills = List.of(new TopSkillItem(UUID.randomUUID(), "Java", 42));

        when(applicationServiceClient.getSystemStats()).thenReturn(new DownstreamApiResponse<>(200, "ok", systemStats));
        when(userServiceClient.getUserPlatformStats()).thenReturn(new DownstreamApiResponse<>(200, "ok", userStats));
        when(jobStatsServiceClient.getJobPlatformStats()).thenReturn(new DownstreamApiResponse<>(200, "ok", jobStats));
        when(jobServiceClient.getTopSkills(anyInt())).thenReturn(new DownstreamApiResponse<>(200, "ok", topSkills));

        AdminDashboardResponse result = adminDashboardService.getDashboard();

        assertThat(result.applicationStats()).isEqualTo(systemStats);
        assertThat(result.userStats()).isEqualTo(userStats);
        assertThat(result.jobStats()).isEqualTo(jobStats);
        assertThat(result.topSkills()).containsExactly(topSkills.get(0));
        assertThat(result.auditStats()).isEqualTo(emptyAuditStats);
    }

    @Test
    void getDashboard_appStatsNull_whenApplicationServiceClientFails() {
        when(applicationServiceClient.getSystemStats()).thenThrow(new RuntimeException("application-service down"));
        when(userServiceClient.getUserPlatformStats()).thenReturn(null);
        when(jobStatsServiceClient.getJobPlatformStats()).thenReturn(null);
        when(jobServiceClient.getTopSkills(anyInt())).thenReturn(null);

        AdminDashboardResponse result = adminDashboardService.getDashboard();

        assertThat(result.applicationStats()).isNull();
        assertThat(result.userStats()).isNull();
        assertThat(result.jobStats()).isNull();
        assertThat(result.topSkills()).isEmpty();
    }

    @Test
    void getDashboard_userStatsNull_whenResponseDataNull() {
        when(applicationServiceClient.getSystemStats()).thenReturn(null);
        when(userServiceClient.getUserPlatformStats()).thenReturn(new DownstreamApiResponse<>(200, "ok", null));
        when(jobStatsServiceClient.getJobPlatformStats()).thenReturn(null);
        when(jobServiceClient.getTopSkills(anyInt())).thenReturn(null);

        AdminDashboardResponse result = adminDashboardService.getDashboard();

        assertThat(result.applicationStats()).isNull();
        assertThat(result.userStats()).isNull();
    }

    @Test
    void getDashboard_topSkillsEmpty_whenJobServiceClientThrows() {
        when(applicationServiceClient.getSystemStats()).thenReturn(null);
        when(userServiceClient.getUserPlatformStats()).thenReturn(null);
        when(jobStatsServiceClient.getJobPlatformStats()).thenReturn(null);
        when(jobServiceClient.getTopSkills(anyInt())).thenThrow(new RuntimeException("job-service down"));

        AdminDashboardResponse result = adminDashboardService.getDashboard();

        assertThat(result.topSkills()).isEmpty();
    }

    @Test
    void getDashboard_topSkillsEmpty_whenResponseDataNull() {
        when(applicationServiceClient.getSystemStats()).thenReturn(null);
        when(userServiceClient.getUserPlatformStats()).thenReturn(null);
        when(jobStatsServiceClient.getJobPlatformStats()).thenReturn(null);
        when(jobServiceClient.getTopSkills(anyInt())).thenReturn(new DownstreamApiResponse<>(200, "ok", null));

        AdminDashboardResponse result = adminDashboardService.getDashboard();

        assertThat(result.topSkills()).isEmpty();
    }

    @Test
    void getDashboard_jobStatsNull_whenJobStatsServiceClientThrows() {
        when(applicationServiceClient.getSystemStats()).thenReturn(null);
        when(userServiceClient.getUserPlatformStats()).thenReturn(null);
        when(jobStatsServiceClient.getJobPlatformStats()).thenThrow(new RuntimeException("job-stats-service down"));
        when(jobServiceClient.getTopSkills(anyInt())).thenReturn(null);

        AdminDashboardResponse result = adminDashboardService.getDashboard();

        assertThat(result.jobStats()).isNull();
    }
}
