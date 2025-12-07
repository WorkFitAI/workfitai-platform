package org.workfitai.applicationservice.validation;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.port.outbound.JobServicePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates that the job exists and is in PUBLISHED status.
 * Order: 3 (runs after file validation - external call)
 *
 * Makes HTTP call to job-service via JobServicePort to validate job availability.
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class JobValidator implements ApplicationValidator {

    private final JobServicePort jobServicePort;

    @Override
    public void validate(CreateApplicationRequest request, String username) {
        String jobId = request.getJobId();
        log.debug("Validating job existence and status: {}", jobId);

        // Make actual HTTP call to job-service
        // This will throw NotFoundException if job doesn't exist or is not PUBLISHED
        if (!jobServicePort.jobExists(jobId)) {
            log.error("Job validation failed - job not found or not published: {}", jobId);
            throw new NotFoundException("Job not found or not available for applications: " + jobId);
        }

        log.debug("Job validation passed: {}", jobId);
    }

    @Override
    public int getOrder() {
        return 3;
    }
}
