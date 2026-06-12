package org.workfitai.jobservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.workfitai.jobservice.model.dto.request.Recommendation.ReqJobRecommendationDTO;

import java.util.Map;

@FeignClient(name = "recommendation-engine", url = "${service.recommendation.url:http://localhost:8001}")
public interface RecommendationFeignClient {

    @PostMapping("/api/v1/recommendations/by-profile")
    Map<String, Object> getRecommendationsByProfile(@RequestBody ReqJobRecommendationDTO request);

    @PostMapping("/api/v1/recommendations/similar-jobs")
    Map<String, Object> getSimilarJobs(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/cv-ranking/rank-by-job/{jobId}")
    Map<String, Object> rankCvsByJob(@PathVariable("jobId") String jobId);
}
