package org.workfitai.applicationservice.validation;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the validation pipeline by running all validators in order.
 * Part of Validation Pipeline Pattern.
 * 
 * Pipeline order:
 * 1. DuplicateApplicationValidator - Check for existing application (local,
 * fast)
 * 2. FileValidator - Validate CV file type and size (local, fast)
 * 3. JobValidator - Verify job exists in job-service (remote, slow)
 */
@Component
@Slf4j
public class ValidationPipeline {

    private final List<ApplicationValidator> validators;

    public ValidationPipeline(List<ApplicationValidator> validators) {
        // Sort validators by order
        this.validators = validators.stream()
                .sorted(Comparator.comparingInt(ApplicationValidator::getOrder))
                .toList();

        log.info("Validation pipeline initialized with {} validators: {}",
                validators.size(),
                validators.stream()
                        .map(v -> v.getClass().getSimpleName())
                        .toList());
    }

    /**
     * Run all validators in the pipeline.
     * Fails fast on first validation error.
     *
     * @param request  The application request to validate
     * @param username The authenticated username
     * @throws RuntimeException if any validation fails
     */
    public void validate(CreateApplicationRequest request, String username) {
        log.info("Starting validation pipeline for user: {}, job: {}", username, request.getJobId());

        for (ApplicationValidator validator : validators) {
            log.debug("Running validator: {}", validator.getClass().getSimpleName());
            validator.validate(request, username);
        }

        log.info("Validation pipeline completed successfully");
    }
}
