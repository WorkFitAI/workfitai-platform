package org.workfitai.applicationservice.adapter.outbound;

import java.time.Instant;
import java.util.List;
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

            log.debug("Job data received: {}", jobData);

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
            String companyId = company != null ? (String) company.get("companyNo") : null;
            String companyDescription = company != null ? (String) company.get("description") : null;
            String companyAddress = company != null ? (String) company.get("address") : null;
            String companyWebsiteUrl = company != null ? (String) company.get("websiteUrl") : null;
            String companyLogoUrl = company != null ? (String) company.get("logoUrl") : null;
            String companySize = company != null ? (String) company.get("size") : null;

            // Extract skill names
            @SuppressWarnings("unchecked")
            List<String> skillNames = (List<String>) jobData.get("skillNames");

            log.info("Company info for job {}: name={}, id={}", jobId, companyName, companyId);

            JobInfo jobInfo = JobInfo.builder()
                    .postId((String) jobData.get("postId"))
                    .title((String) jobData.get("title"))
                    .shortDescription((String) jobData.get("shortDescription"))
                    .description((String) jobData.get("description"))
                    .employmentType((String) jobData.get("employmentType"))
                    .experienceLevel((String) jobData.get("experienceLevel"))
                    .educationLevel((String) jobData.get("educationLevel"))
                    .requiredExperience((String) jobData.get("requiredExperience"))
                    .salaryMin(
                            jobData.get("salaryMin") != null ? ((Number) jobData.get("salaryMin")).doubleValue() : null)
                    .salaryMax(
                            jobData.get("salaryMax") != null ? ((Number) jobData.get("salaryMax")).doubleValue() : null)
                    .currency((String) jobData.get("currency"))
                    .location((String) jobData.get("location"))
                    .quantity(jobData.get("quantity") != null ? ((Number) jobData.get("quantity")).intValue() : null)
                    .totalApplications(jobData.get("totalApplications") != null
                            ? ((Number) jobData.get("totalApplications")).intValue()
                            : null)
                    .createdDate(jobData.get("createdDate") != null ? Instant.parse((String) jobData.get("createdDate"))
                            : null)
                    .lastModifiedDate(jobData.get("lastModifiedDate") != null
                            ? Instant.parse((String) jobData.get("lastModifiedDate"))
                            : null)
                    .expiresAt(
                            jobData.get("expiresAt") != null ? Instant.parse((String) jobData.get("expiresAt")) : null)
                    .status(status)
                    .skillNames(skillNames)
                    .bannerUrl((String) jobData.get("bannerUrl"))
                    .createdBy((String) jobData.get("createdBy"))
                    .companyId(companyId)
                    .companyName(companyName)
                    .companyDescription(companyDescription)
                    .companyAddress(companyAddress)
                    .companyWebsiteUrl(companyWebsiteUrl)
                    .companyLogoUrl(companyLogoUrl)
                    .companySize(companySize)
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
