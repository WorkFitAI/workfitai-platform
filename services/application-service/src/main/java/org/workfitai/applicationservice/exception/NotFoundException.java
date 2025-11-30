package org.workfitai.applicationservice.exception;

/**
 * Exception thrown when a requested resource cannot be found.
 * 
 * This exception results in HTTP 404 Not Found response.
 * 
 * Common scenarios:
 * - Application ID doesn't exist in database
 * - Referenced Job not found in job-service
 * - Referenced CV not found in cv-service
 * 
 * Usage:
 * 
 * <pre>
 * Application app = repository.findById(id)
 *         .orElseThrow(() -> new NotFoundException(ValidationMessages.APPLICATION_NOT_FOUND));
 * </pre>
 */
public class NotFoundException extends RuntimeException {

    /**
     * Creates a new not found exception with the specified message.
     * 
     * @param message Description of what was not found
     */
    public NotFoundException(String message) {
        super(message);
    }

    /**
     * Creates a new not found exception with message and cause.
     * 
     * @param message Description of what was not found
     * @param cause   The underlying cause (e.g., FeignException.NotFound)
     */
    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
