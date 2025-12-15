package org.workfitai.applicationservice.exception;

/**
 * Exception thrown when a request is invalid or cannot be processed.
 *
 * This exception results in HTTP 400 Bad Request response.
 *
 * Common scenarios:
 * - Invalid input data (e.g., missing required CV file)
 * - Business rule violation (e.g., duplicate draft for same job)
 * - Invalid state transition (e.g., updating submitted application as draft)
 * - Validation failures
 *
 * Usage:
 *
 * <pre>
 * if (repository.existsByUsernameAndJobId(username, jobId)) {
 *     throw new BadRequestException("You have already applied to this job");
 * }
 * </pre>
 */
public class BadRequestException extends RuntimeException {

    /**
     * Creates a new bad request exception with the specified message.
     *
     * @param message Description of why the request is invalid
     */
    public BadRequestException(String message) {
        super(message);
    }

    /**
     * Creates a new bad request exception with message and cause.
     *
     * @param message Description of why the request is invalid
     * @param cause   The underlying cause
     */
    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
