package org.workfitai.applicationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.workfitai.applicationservice.client.dto.CvDTO;
import org.workfitai.applicationservice.dto.response.RestResponse;

/**
 * Feign client for communicating with cv-service.
 * 
 * Configuration:
 * - name: "cv" matches the Consul service registration name
 * - url: Optional override for local development
 * 
 * Service Discovery:
 * - In Docker: Consul discovers "cv" service automatically
 * - In Local: Can override with cv-service.url property
 * 
 * Usage:
 * 
 * <pre>
 * RestResponse<CvDTO> response = cvServiceClient.getCvById("uuid");
 * if (response.getData() != null && response.getData().belongsToUser(userId)) {
 *     // CV ownership verified, proceed with application
 * }
 * </pre>
 * 
 * Error Handling:
 * - 404 from cv-service → FeignException.NotFound → handled by
 * GlobalExceptionHandler
 * - Network errors → FeignException → mapped to 503 Service Unavailable
 * 
 * @see org.workfitai.applicationservice.exception.GlobalExceptionHandler
 */
@FeignClient(name = "cv", // Consul service name
        path = "/api/v1/cvs", configuration = FeignClientConfig.class)
public interface CvServiceClient {

    /**
     * Retrieves CV details by ID.
     * 
     * Endpoint: GET /api/v1/cvs/{cvId}
     * 
     * Used for:
     * 1. Validating CV exists before creating application
     * 2. Verifying CV ownership (belongTo must match userId)
     * 3. Getting CV details for application response
     * 
     * @param cvId CV identifier
     * @return RestResponse containing CvDTO
     * @throws feign.FeignException.NotFound if CV doesn't exist
     */
    @GetMapping("/{cvId}")
    RestResponse<CvDTO> getCvById(@PathVariable("cvId") String cvId);
}
