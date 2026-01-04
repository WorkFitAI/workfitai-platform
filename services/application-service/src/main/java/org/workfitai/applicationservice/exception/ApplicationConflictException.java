package org.workfitai.applicationservice.exception;

/**
 * Exception thrown when a duplicate application is detected.
 * 
 * Business rule: A user can only have one active application per job.
 * 
 * This exception results in HTTP 409 Conflict response.
 * 
 * Usage:
 * 
 * <pre>
 * if (repository.existsByUserIdAndJobId(userId, jobId)) {
 *     throw new ApplicationConflictException(ValidationMessages.APPLICATION_ALREADY_EXISTS);
 * }
 * </pre>
 */
public class ApplicationConflictException extends RuntimeException {

    /**
     * Creates a new conflict exception with the specified message.
     * 
     * @param message Description of the conflict (e.g., "You have already applied
     *                to this job")
     */
    public ApplicationConflictException(String message) {
        super(message);
    }

    /**
     * Creates a new conflict exception with message and cause.
     * 
     * @param message Description of the conflict
     * @param cause   The underlying cause of the conflict
     */
    public ApplicationConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
