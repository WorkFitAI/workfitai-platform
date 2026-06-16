package org.workfitai.applicationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.workfitai.applicationservice.config.InternalFeignConfig;
import org.workfitai.applicationservice.dto.response.CvRankingResponse;

/**
 * Feign client for recommendation-engine CV ranking API.
 * Uses InternalFeignConfig (no JWT) — recommendation-engine relies on
 * Kafka-synced applicant/CV data, not the caller's auth token.
 */
@FeignClient(
        name = "recommendation-engine",
        url = "${service.recommendation.url:http://localhost:8001}",
        configuration = InternalFeignConfig.class
)
public interface RecommendationEngineClient {

    @PostMapping("/api/v1/cv-ranking/rank-by-job/{jobId}")
    CvRankingResponse rankCvsByJob(@PathVariable("jobId") String jobId);
}
