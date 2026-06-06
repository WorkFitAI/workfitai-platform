package org.workfitai.monitoringservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.workfitai.monitoringservice.dto.downstream.DownstreamApiResponse;
import org.workfitai.monitoringservice.dto.downstream.ManagerStatsSummary;
import org.workfitai.monitoringservice.dto.downstream.SystemStatsSummary;

/**
 * Feign client for application-service.
 * JWT is forwarded automatically via FeignJwtInterceptor.
 * Admin calls use ROLE_ADMIN token; HRM calls use ROLE_HR_MANAGER token with application:manage authority.
 */
@FeignClient(name = "application")
public interface ApplicationServiceClient {

    @GetMapping("/admin/stats")
    DownstreamApiResponse<SystemStatsSummary> getSystemStats();

    @GetMapping("/manager/stats")
    DownstreamApiResponse<ManagerStatsSummary> getManagerStats(@RequestParam String companyId);
}
