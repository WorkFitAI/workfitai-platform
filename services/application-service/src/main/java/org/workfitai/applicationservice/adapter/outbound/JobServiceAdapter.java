package org.workfitai.applicationservice.adapter.outbound;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.workfitai.applicationservice.dto.JobInfo;
import org.workfitai.applicationservice.port.outbound.JobServicePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Job-service HTTP client adapter.
 * Part of Hexagonal Architecture - infrastructure adapter.
 * 
 * TODO: Implement actual HTTP call to job-service when available.
 * Currently returns stub data for development/testing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobServiceAdapter implements JobServicePort {

    // TODO: Inject WebClient or RestTemplate for HTTP calls
    // private final WebClient jobServiceClient;

    @Override
    public JobInfo validateAndGetJob(String jobId) {
        log.info("[JOB-SERVICE-STUB] Validating job: {}", jobId);

        // TODO: Replace with actual HTTP call to job-service
        // Example:
        // return jobServiceClient.get()
        // .uri("/api/jobs/{jobId}", jobId)
        // .retrieve()
        // .bodyToMono(JobInfo.class)
        // .block();

        // STUB: Return mock job info - always success
        log.warn("[JOB-SERVICE-STUB] Using stub implementation - returning mock job info");

        return JobInfo.builder()
                .id(jobId)
                .title("Software Engineer")
                .companyName("TechCorp Inc.")
                .location("Remote")
                .employmentType("FULL_TIME")
                .experienceLevel("MID")
                .status("PUBLISHED")
                .fetchedAt(Instant.now())
                .build();
    }

    @Override
    public boolean jobExists(String jobId) {
        log.info("[JOB-SERVICE-STUB] Checking if job exists: {}", jobId);

        // TODO: Replace with actual HTTP call
        // return jobServiceClient.head()
        // .uri("/api/jobs/{jobId}", jobId)
        // .exchangeToMono(response ->
        // Mono.just(response.statusCode().is2xxSuccessful()))
        // .block();

        // STUB: Always return true
        log.warn("[JOB-SERVICE-STUB] Using stub implementation - returning true");
        return true;
    }
}
