package org.workfitai.monitoringservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.workfitai.monitoringservice.dto.downstream.DownstreamApiResponse;
import org.workfitai.monitoringservice.dto.downstream.TopSkillItem;

import java.util.List;

/**
 * Feign client for job-service.
 * JWT is forwarded automatically via FeignJwtInterceptor.
 */
@FeignClient(name = "job", contextId = "jobServiceClient")
public interface JobServiceClient {

    @GetMapping("/admin/skills/top")
    DownstreamApiResponse<List<TopSkillItem>> getTopSkills(@RequestParam(defaultValue = "20") int limit);
}
