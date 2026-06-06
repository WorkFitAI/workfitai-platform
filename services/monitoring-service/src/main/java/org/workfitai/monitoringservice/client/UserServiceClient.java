package org.workfitai.monitoringservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.workfitai.monitoringservice.dto.downstream.DownstreamApiResponse;
import org.workfitai.monitoringservice.dto.downstream.UserStatsSummary;

/**
 * Feign client for user-service internal stats endpoint.
 * JWT is forwarded automatically via FeignJwtInterceptor.
 */
@FeignClient(name = "user", contextId = "userServiceClient")
public interface UserServiceClient {

    @GetMapping("/api/v1/internal/admin/stats")
    DownstreamApiResponse<UserStatsSummary> getUserPlatformStats();
}
