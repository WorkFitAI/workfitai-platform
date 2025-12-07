package org.workfitai.applicationservice.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.workfitai.applicationservice.dto.response.RestResponse;

/**
 * Feign client for job-service API calls.
 *
 * Used for:
 * - Validating job existence and availability
 * - Fetching job details for application snapshot
 *
 * Service Discovery:
 * - Uses Consul service name "job" (matches job-service's Consul registration)
 * - In Docker: Consul discovers job-service automatically
 * - In Local: Can override with job.url property
 *
 * Path Configuration:
 * - Feign client name "job" routes to the service via Consul
 * - Path "/public/jobs" is the controller's @RequestMapping
 * - Final URL: http://job/public/jobs/{id}
 */
@FeignClient(name = "job", path = "/public/jobs")
public interface JobServiceClient {

    /**
     * Retrieves job details by job ID.
     *
     * Endpoint: GET /public/jobs/{id} (via job-service)
     *
     * Response format:
     * {
     *   "statusCode": 200,
     *   "data": {
     *     "postId": "uuid",
     *     "title": "Software Engineer",
     *     "description": "...",
     *     "employmentType": "FULL_TIME",
     *     "experienceLevel": "MID",
     *     "salaryMin": 50000,
     *     "salaryMax": 80000,
     *     "currency": "USD",
     *     "expiresAt": "2025-12-31T23:59:59Z",
     *     "status": "PUBLISHED",
     *     "educationLevel": "BACHELOR",
     *     "skillNames": ["Java", "Spring Boot"],
     *     "company": {
     *       "id": "uuid",
     *       "name": "TechCorp Inc.",
     *       "location": "Remote"
     *     },
     *     "createdDate": "2025-01-01T00:00:00Z"
     *   },
     *   "message": "Job detail fetched successfully"
     * }
     *
     * @param id Job ID (UUID as string)
     * @return RestResponse containing job details
     */
    @GetMapping("/{id}")
    RestResponse<Map<String, Object>> getJobById(@PathVariable("id") String id);
}
