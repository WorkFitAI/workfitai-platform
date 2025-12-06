package org.workfitai.applicationservice.validation;

import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;

/**
 * Base interface for validation pipeline steps.
 * Part of Validation Pipeline Pattern.
 * 
 * Each validator focuses on a single concern and throws
 * a specific exception if validation fails.
 */
public interface ApplicationValidator {

    /**
     * Validate the application request.
     *
     * @param request  The application request to validate
     * @param username The authenticated username
     * @throws ValidationException if validation fails
     */
    void validate(CreateApplicationRequest request, String username);

    /**
     * Get the order of this validator in the pipeline.
     * Lower numbers execute first.
     */
    int getOrder();
}
