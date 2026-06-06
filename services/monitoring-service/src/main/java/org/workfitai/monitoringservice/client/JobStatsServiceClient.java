package org.workfitai.monitoringservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.workfitai.monitoringservice.dto.downstream.DownstreamApiResponse;
import org.workfitai.monitoringservice.dto.downstream.HrmJobStatsSummary;
import org.workfitai.monitoringservice.dto.downstream.JobStatsSummary;

/**
 * Feign client for job-service stats endpoints (admin platform-wide and company-scoped).
 * Uses contextId to avoid Spring bean conflict with the existing JobServiceClient.
 * JWT is forwarded automatically via FeignJwtInterceptor.
 */
@FeignClient(name = "job", contextId = "jobStatsServiceClient")
public interface JobStatsServiceClient {

    @GetMapping("/admin/job-stats")
    DownstreamApiResponse<JobStatsSummary> getJobPlatformStats();

    @GetMapping("/internal/company/{companyId}/job-stats")
    DownstreamApiResponse<HrmJobStatsSummary> getCompanyJobStats(@PathVariable String companyId);
}
