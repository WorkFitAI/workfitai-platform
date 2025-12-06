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
 * TODO: Currently using stub implementation that always succeeds.
 * Replace with actual HTTP call to job-service when available.
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
        log.debug("Validating job existence: {}", jobId);

        // TODO: This currently uses stub that always returns true
        // When job-service is available, this will make an actual HTTP call
        if (!jobServicePort.jobExists(jobId)) {
            throw new NotFoundException("Job not found: " + jobId);
        }

        log.debug("Job validation passed: {}", jobId);
    }

    @Override
    public int getOrder() {
        return 3;
    }
}
