package org.workfitai.monitoringservice.service;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.monitoringservice.client.ApplicationServiceClient;
import org.workfitai.monitoringservice.client.JobStatsServiceClient;
import org.workfitai.monitoringservice.dto.AuditEventResponse;
import org.workfitai.monitoringservice.dto.AuditStatsResponse;
import org.workfitai.monitoringservice.dto.HrmDashboardResponse;
import org.workfitai.monitoringservice.dto.downstream.DownstreamApiResponse;
import org.workfitai.monitoringservice.dto.downstream.HrmJobStatsSummary;
import org.workfitai.monitoringservice.dto.downstream.ManagerStatsSummary;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrmDashboardServiceTest {

    @Mock
    private ApplicationServiceClient applicationServiceClient;
    @Mock
    private JobStatsServiceClient jobStatsServiceClient;
    @Mock
    private AuditSearchService auditSearchService;

    @InjectMocks
    private HrmDashboardService hrmDashboardService;

    private static final String COMPANY_ID = "company-1";

    @BeforeEach
    void setUp() {
        lenient().when(auditSearchService.getRecentErrorLogsForCompany(eq(COMPANY_ID), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
    }

    @Test
    void getDashboard_aggregatesAllSources_onSuccess() {
        ManagerStatsSummary managerStats = new ManagerStatsSummary(10L, Map.of(), List.of(), 0L, Map.of(), List.of(), 0L, 0L);
        HrmJobStatsSummary jobStats = new HrmJobStatsSummary(5, 1, 0, 0, 0, Map.of(), Map.of(), Map.of(), List.of());
        AuditStatsResponse auditStats = new AuditStatsResponse(3, 0, 100.0, 2, Map.of(), Map.of(), Map.of());
        List<AuditEventResponse> recentAuditErrors = List.of(
                new AuditEventResponse("evt-1", "hr1", "HR_MANAGER", COMPANY_ID, "USER", "user-9",
                        "AUTH_LOGIN_FAILED", null, null, java.time.Instant.parse("2024-01-01T00:00:00Z"),
                        "hr1 failed to log in", false, "bad credentials", "127.0.0.1"));

        when(applicationServiceClient.getManagerStats(COMPANY_ID)).thenReturn(new DownstreamApiResponse<>(200, "ok", managerStats));
        when(jobStatsServiceClient.getCompanyJobStats(COMPANY_ID)).thenReturn(new DownstreamApiResponse<>(200, "ok", jobStats));
        when(auditSearchService.getAuditStatsForCompany(COMPANY_ID)).thenReturn(auditStats);
        when(auditSearchService.getRecentErrorLogsForCompany(COMPANY_ID, 10)).thenReturn(recentAuditErrors);

        HrmDashboardResponse result = hrmDashboardService.getDashboard(COMPANY_ID);

        assertThat(result.applicationStats()).isEqualTo(managerStats);
        assertThat(result.jobStats()).isEqualTo(jobStats);
        assertThat(result.auditStats()).isEqualTo(auditStats);
        assertThat(result.recentAuditErrors()).isEqualTo(recentAuditErrors);
    }

    @Test
    void getDashboard_managerStatsNull_whenApplicationServiceClientThrowsFeignException() {
        FeignException feignException = mock(FeignException.class);
        when(feignException.status()).thenReturn(403);
        when(feignException.getMessage()).thenReturn("Forbidden");
        when(applicationServiceClient.getManagerStats(COMPANY_ID)).thenThrow(feignException);

        HrmJobStatsSummary jobStats = new HrmJobStatsSummary(1, 0, 0, 0, 0, Map.of(), Map.of(), Map.of(), List.of());
        when(jobStatsServiceClient.getCompanyJobStats(COMPANY_ID)).thenReturn(new DownstreamApiResponse<>(200, "ok", jobStats));
        when(auditSearchService.getAuditStatsForCompany(COMPANY_ID))
                .thenReturn(new AuditStatsResponse(0, 0, 100.0, 0, Map.of(), Map.of(), Map.of()));

        HrmDashboardResponse result = hrmDashboardService.getDashboard(COMPANY_ID);

        assertThat(result.applicationStats()).isNull();
        assertThat(result.jobStats()).isEqualTo(jobStats);
    }

    @Test
    void getDashboard_jobStatsNull_whenJobStatsClientThrowsGenericException() {
        ManagerStatsSummary managerStats = new ManagerStatsSummary(0L, Map.of(), List.of(), 0L, Map.of(), List.of(), 0L, 0L);
        when(applicationServiceClient.getManagerStats(COMPANY_ID)).thenReturn(new DownstreamApiResponse<>(200, "ok", managerStats));
        when(jobStatsServiceClient.getCompanyJobStats(COMPANY_ID)).thenThrow(new RuntimeException("timeout"));
        when(auditSearchService.getAuditStatsForCompany(COMPANY_ID))
                .thenReturn(new AuditStatsResponse(0, 0, 100.0, 0, Map.of(), Map.of(), Map.of()));

        HrmDashboardResponse result = hrmDashboardService.getDashboard(COMPANY_ID);

        assertThat(result.applicationStats()).isEqualTo(managerStats);
        assertThat(result.jobStats()).isNull();
    }

    @Test
    void getDashboard_managerStatsNull_whenDownstreamResponseDataNull() {
        when(applicationServiceClient.getManagerStats(eq(COMPANY_ID)))
                .thenReturn(new DownstreamApiResponse<>(200, "ok", null));
        when(jobStatsServiceClient.getCompanyJobStats(COMPANY_ID)).thenReturn(null);
        when(auditSearchService.getAuditStatsForCompany(COMPANY_ID))
                .thenReturn(new AuditStatsResponse(0, 0, 100.0, 0, Map.of(), Map.of(), Map.of()));

        HrmDashboardResponse result = hrmDashboardService.getDashboard(COMPANY_ID);

        assertThat(result.applicationStats()).isNull();
        assertThat(result.jobStats()).isNull();
    }
}
