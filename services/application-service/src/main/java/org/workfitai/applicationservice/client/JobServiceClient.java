package org.workfitai.applicationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.workfitai.applicationservice.client.dto.JobDTO;
import org.workfitai.applicationservice.dto.response.RestResponse;

/**
 * Feign client for communicating with job-service.
 * 
 * Configuration:
 * - name: "job" matches the Consul service registration name
 * - url: Optional override for local development (set in application.yml)
 * 
 * Service Discovery:
 * - In Docker: Consul discovers "job" service automatically
 * - In Local: Can override with job-service.url property
 * 
 * Usage:
 * 
 * <pre>
 * RestResponse<JobDTO> response = jobServiceClient.getJobById("uuid");
 * if (response.getData() != null && response.getData().isAcceptingApplications()) {
 *     // Process application
 * }
 * </pre>
 * 
 * Error Handling:
 * - 404 from job-service → FeignException.NotFound → handled by
 * GlobalExceptionHandler
 * - Network errors → FeignException → mapped to 503 Service Unavailable
 * 
 * @see org.workfitai.applicationservice.exception.GlobalExceptionHandler
 */
@FeignClient(name = "job", // Consul service name
        path = "/api/v1/jobs", configuration = FeignClientConfig.class)
public interface JobServiceClient {

    /**
     * Retrieves job details by ID.
     * 
     * Endpoint: GET /api/v1/jobs/{jobId}
     * 
     * Used for:
     * 1. Validating job exists before creating application
     * 2. Checking job status is PUBLISHED
     * 3. Getting job details for application response
     * 
     * @param jobId Job UUID
     * @return RestResponse containing JobDTO
     * @throws feign.FeignException.NotFound if job doesn't exist
     */
    @GetMapping("/{jobId}")
    RestResponse<JobDTO> getJobById(@PathVariable("jobId") String jobId);
}
