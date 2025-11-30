package org.workfitai.applicationservice.exception;

/**
 * Exception thrown when a cross-service call fails.
 * 
 * This exception results in HTTP 503 Service Unavailable response.
 * 
 * Common scenarios:
 * - job-service is down or unreachable
 * - cv-service is down or unreachable
 * - Network timeout during Feign call
 * - Circuit breaker is open
 * 
 * Usage:
 * 
 * <pre>
 * try {
 *     return jobServiceClient.getJob(jobId);
 * } catch (FeignException e) {
 *     throw new ServiceUnavailableException(ValidationMessages.SERVICE_UNAVAILABLE, e);
 * }
 * </pre>
 */
public class ServiceUnavailableException extends RuntimeException {

    /**
     * Creates a new service unavailable exception with the specified message.
     * 
     * @param message Description of the unavailable service
     */
    public ServiceUnavailableException(String message) {
        super(message);
    }

    /**
     * Creates a new service unavailable exception with message and cause.
     * 
     * @param message Description of the unavailable service
     * @param cause   The underlying cause (e.g., FeignException,
     *                SocketTimeoutException)
     */
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
