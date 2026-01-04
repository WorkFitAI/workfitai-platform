package org.workfitai.applicationservice.exception;

/**
 * Exception thrown when a user attempts an action they're not authorized to
 * perform.
 * 
 * This is different from authentication failure (401 Unauthorized).
 * ForbiddenException is for authenticated users who lack permission for a
 * specific action.
 * 
 * This exception results in HTTP 403 Forbidden response.
 * 
 * Common scenarios:
 * - Applying with a CV that doesn't belong to the user
 * - Applying to a job that is not PUBLISHED (DRAFT or CLOSED)
 * - Accessing another user's application
 * 
 * Usage:
 * 
 * <pre>
 * if (!cv.getBelongTo().equals(userId)) {
 *     throw new ForbiddenException(ValidationMessages.CV_NOT_OWNED_BY_USER);
 * }
 * </pre>
 */
public class ForbiddenException extends RuntimeException {

    /**
     * Creates a new forbidden exception with the specified message.
     * 
     * @param message Description of why access is forbidden
     */
    public ForbiddenException(String message) {
        super(message);
    }

    /**
     * Creates a new forbidden exception with message and cause.
     * 
     * @param message Description of why access is forbidden
     * @param cause   The underlying cause
     */
    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
