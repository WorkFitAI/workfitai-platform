package org.workfitai.applicationservice.adapter.outbound;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.workfitai.applicationservice.client.JobServiceClient;
import org.workfitai.applicationservice.dto.JobInfo;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.port.outbound.JobServicePort;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Job-service HTTP client adapter.
 * Part of Hexagonal Architecture - infrastructure adapter.
 *
 * Uses Feign client for HTTP calls to job-service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobServiceAdapter implements JobServicePort {

    private final JobServiceClient jobServiceClient;

    @Override
    public JobInfo validateAndGetJob(String jobId) {
        log.info("Validating and fetching job from job-service: {}", jobId);

        try {
            RestResponse<Map<String, Object>> response = jobServiceClient.getJobById(jobId);

            if (response == null || response.getData() == null) {
                log.error("Job-service returned null response for job: {}", jobId);
                throw new NotFoundException("Job not found: " + jobId);
            }

            Map<String, Object> jobData = response.getData();

            // Validate job status
            String status = (String) jobData.get("status");
            if (!"PUBLISHED".equals(status)) {
                log.warn("Job {} is not in PUBLISHED status: {}", jobId, status);
                throw new NotFoundException("Job is not available for applications: " + jobId);
            }

            // Extract company info
            @SuppressWarnings("unchecked")
            Map<String, Object> company = (Map<String, Object>) jobData.get("company");
            String companyName = company != null ? (String) company.get("name") : "Unknown";
            String location = company != null ? (String) company.get("address") : "Not specified";

            JobInfo jobInfo = JobInfo.builder()
                    .id(jobId)
                    .title((String) jobData.get("title"))
                    .companyName(companyName)
                    .location(location)
                    .employmentType(jobData.get("employmentType") != null ?
                            jobData.get("employmentType").toString() : null)
                    .experienceLevel(jobData.get("experienceLevel") != null ?
                            jobData.get("experienceLevel").toString() : null)
                    .status(status)
                    .createdBy((String) jobData.get("createdBy"))
                    .fetchedAt(Instant.now())
                    .build();

            log.info("Successfully fetched job info: {}", jobId);
            return jobInfo;

        } catch (FeignException.NotFound e) {
            log.error("Job not found in job-service: {}", jobId);
            throw new NotFoundException("Job not found: " + jobId);
        } catch (FeignException e) {
            log.error("Error calling job-service for job {}: {} - {}", jobId, e.status(), e.getMessage());
            throw new RuntimeException("Failed to validate job: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean jobExists(String jobId) {
        log.info("Checking if job exists: {}", jobId);

        try {
            RestResponse<Map<String, Object>> response = jobServiceClient.getJobById(jobId);

            if (response == null || response.getData() == null) {
                return false;
            }

            // Check if job is in PUBLISHED status
            Map<String, Object> jobData = response.getData();
            String status = (String) jobData.get("status");
            boolean isPublished = "PUBLISHED".equals(status);

            log.info("Job {} exists and is published: {}", jobId, isPublished);
            return isPublished;

        } catch (FeignException.NotFound e) {
            log.debug("Job not found: {}", jobId);
            return false;
        } catch (FeignException e) {
            log.error("Error checking job existence for {}: {} - {}", jobId, e.status(), e.getMessage());
            return false;
        }
    }
}
