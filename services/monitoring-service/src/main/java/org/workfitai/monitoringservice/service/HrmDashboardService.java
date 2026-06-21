package org.workfitai.monitoringservice.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.workfitai.monitoringservice.client.ApplicationServiceClient;
import org.workfitai.monitoringservice.client.JobStatsServiceClient;
import org.workfitai.monitoringservice.dto.AuditStatsResponse;
import org.workfitai.monitoringservice.dto.HrmDashboardResponse;
import org.workfitai.monitoringservice.dto.downstream.DownstreamApiResponse;
import org.workfitai.monitoringservice.dto.downstream.HrmJobStatsSummary;
import org.workfitai.monitoringservice.dto.downstream.ManagerStatsSummary;

@Service
@RequiredArgsConstructor
@Slf4j
public class HrmDashboardService {

    private final ApplicationServiceClient applicationServiceClient;
    private final JobStatsServiceClient jobStatsServiceClient;
    private final AuditSearchService auditSearchService;

    public HrmDashboardResponse getDashboard(String companyId) {
        ManagerStatsSummary managerStats = fetchManagerStats(companyId);
        HrmJobStatsSummary jobStats      = fetchCompanyJobStats(companyId);
        AuditStatsResponse auditStats    = auditSearchService.getAuditStatsForCompany(companyId);
        return new HrmDashboardResponse(managerStats, jobStats, auditStats);
    }

    private ManagerStatsSummary fetchManagerStats(String companyId) {
        try {
            DownstreamApiResponse<ManagerStatsSummary> response = applicationServiceClient.getManagerStats(companyId);
            return response != null ? response.data() : null;
        } catch (Exception e) {
            logDownstreamFailure("manager stats", companyId, e);
            return null;
        }
    }

    private HrmJobStatsSummary fetchCompanyJobStats(String companyId) {
        try {
            DownstreamApiResponse<HrmJobStatsSummary> response = jobStatsServiceClient.getCompanyJobStats(companyId);
            return response != null ? response.data() : null;
        } catch (Exception e) {
            logDownstreamFailure("job stats", companyId, e);
            return null;
        }
    }

    /**
     * Logs the failure type so a 403 (permission/role drift) is distinguishable
     * from a network/timeout/deserialization failure — both previously collapsed
     * into the same generic message, masking authorization bugs as outages.
     */
    private void logDownstreamFailure(String what, String companyId, Exception e) {
        if (e instanceof FeignException feignEx) {
            log.error("[HRM-DASH] Failed to fetch {} for company {}: HTTP {} - {}",
                    what, companyId, feignEx.status(), feignEx.getMessage());
        } else {
            log.error("[HRM-DASH] Failed to fetch {} for company {}: {}", what, companyId, e.getMessage());
        }
    }
}
