package org.workfitai.monitoringservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.workfitai.monitoringservice.client.ApplicationServiceClient;
import org.workfitai.monitoringservice.client.JobServiceClient;
import org.workfitai.monitoringservice.client.JobStatsServiceClient;
import org.workfitai.monitoringservice.client.UserServiceClient;
import org.workfitai.monitoringservice.dto.AdminDashboardResponse;
import org.workfitai.monitoringservice.dto.AuditEventResponse;
import org.workfitai.monitoringservice.dto.AuditStatsResponse;
import org.workfitai.monitoringservice.dto.downstream.DownstreamApiResponse;
import org.workfitai.monitoringservice.dto.downstream.JobStatsSummary;
import org.workfitai.monitoringservice.dto.downstream.SystemStatsSummary;
import org.workfitai.monitoringservice.dto.downstream.TopSkillItem;
import org.workfitai.monitoringservice.dto.downstream.UserStatsSummary;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardService {

    private final ApplicationServiceClient applicationServiceClient;
    private final JobServiceClient jobServiceClient;
    private final UserServiceClient userServiceClient;
    private final JobStatsServiceClient jobStatsServiceClient;
    private final AuditSearchService auditSearchService;

    public AdminDashboardResponse getDashboard() {
        SystemStatsSummary appStats              = fetchAppStats();
        UserStatsSummary userStats               = fetchUserStats();
        JobStatsSummary jobStats                 = fetchJobStats();
        List<TopSkillItem> topSkills             = fetchTopSkills();
        AuditStatsResponse auditStats            = auditSearchService.getAuditStats(null, null);
        List<AuditEventResponse> recentAuditErrors = auditSearchService.getRecentErrorLogs(20);
        return new AdminDashboardResponse(appStats, userStats, jobStats, topSkills, auditStats, recentAuditErrors);
    }

    private SystemStatsSummary fetchAppStats() {
        try {
            DownstreamApiResponse<SystemStatsSummary> response = applicationServiceClient.getSystemStats();
            return response != null ? response.data() : null;
        } catch (Exception e) {
            log.error("[ADMIN-DASH] Failed to fetch app stats: {}", e.getMessage());
            return null;
        }
    }

    private UserStatsSummary fetchUserStats() {
        try {
            DownstreamApiResponse<UserStatsSummary> response = userServiceClient.getUserPlatformStats();
            return response != null ? response.data() : null;
        } catch (Exception e) {
            log.error("[ADMIN-DASH] Failed to fetch user stats: {}", e.getMessage());
            return null;
        }
    }

    private JobStatsSummary fetchJobStats() {
        try {
            DownstreamApiResponse<JobStatsSummary> response = jobStatsServiceClient.getJobPlatformStats();
            return response != null ? response.data() : null;
        } catch (Exception e) {
            log.error("[ADMIN-DASH] Failed to fetch job stats: {}", e.getMessage());
            return null;
        }
    }

    private List<TopSkillItem> fetchTopSkills() {
        try {
            DownstreamApiResponse<List<TopSkillItem>> response = jobServiceClient.getTopSkills(20);
            return response != null && response.data() != null ? response.data() : List.of();
        } catch (Exception e) {
            log.error("[ADMIN-DASH] Failed to fetch top skills: {}", e.getMessage());
            return List.of();
        }
    }
}
