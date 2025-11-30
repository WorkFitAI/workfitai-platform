package org.workfitai.applicationservice.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.applicationservice.constants.ValidationMessages;
import org.workfitai.applicationservice.dto.response.ApiError;

import feign.FeignException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler for application-service.
 * 
 * Follows the same pattern as user-service's RestExceptionHandler
 * with additional handlers for:
 * - Custom application exceptions (409, 403, 404, 503)
 * - Feign client exceptions (cross-service calls)
 * 
 * HTTP Status Code Mapping:
 * - 400 Bad Request: Validation errors, malformed JSON
 * - 401 Unauthorized: Missing or invalid authentication
 * - 403 Forbidden: Authenticated but not authorized (e.g., wrong CV ownership)
 * - 404 Not Found: Resource not found (application, job, CV)
 * - 409 Conflict: Duplicate application
 * - 500 Internal Server Error: Unexpected errors
 * - 503 Service Unavailable: Downstream service failure
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 CUSTOM APPLICATION EXCEPTIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handles duplicate application attempts.
     * HTTP 409 Conflict
     * 
     * @param ex ApplicationConflictException
     * @return ApiError with 409 status
     */
    @ExceptionHandler(ApplicationConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ApplicationConflictException ex) {
        log.warn("Application conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    /**
     * Handles forbidden access attempts.
     * HTTP 403 Forbidden
     * 
     * Examples:
     * - Using CV that doesn't belong to user
     * - Applying to non-PUBLISHED job
     * 
     * @param ex ForbiddenException
     * @return ApiError with 403 status
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        log.warn("Forbidden access: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    /**
     * Handles resource not found scenarios.
     * HTTP 404 Not Found
     * 
     * @param ex NotFoundException
     * @return ApiError with 404 status
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    /**
     * Handles downstream service failures.
     * HTTP 503 Service Unavailable
     * 
     * @param ex ServiceUnavailableException
     * @return ApiError with 503 status
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleServiceUnavailable(ServiceUnavailableException ex) {
        log.error("Service unavailable: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getMessage()));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 FEIGN CLIENT EXCEPTIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handles Feign client exceptions from cross-service calls.
     * 
     * Maps Feign exceptions to appropriate HTTP responses:
     * - 4xx from downstream → propagate as-is (404, 403, etc.)
     * - 5xx from downstream → 503 Service Unavailable
     * 
     * @param ex FeignException
     * @return ApiError with appropriate status
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiError> handleFeignException(FeignException ex) {
        int status = ex.status();
        String serviceName = extractServiceName(ex);

        log.error("Feign error calling {}: status={}, message={}",
                serviceName, status, ex.getMessage());

        // 4xx client errors - propagate the error
        if (status >= 400 && status < 500) {
            String message = switch (status) {
                case 404 -> ValidationMessages.JOB_NOT_FOUND; // Could be job or CV
                case 403 -> ValidationMessages.ACCESS_DENIED;
                default -> "Request to " + serviceName + " failed";
            };
            return ResponseEntity.status(status)
                    .body(ApiError.of(status, message));
        }

        // 5xx or network errors - service unavailable
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(HttpStatus.SERVICE_UNAVAILABLE.value(),
                        ValidationMessages.SERVICE_UNAVAILABLE));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 VALIDATION EXCEPTIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handles @Valid validation failures on request bodies.
     * HTTP 400 Bad Request
     * 
     * @param ex MethodArgumentNotValidException
     * @return ApiError with validation error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();

        log.warn("Validation failed: {}", errors);
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(),
                        ValidationMessages.VALIDATION_ERROR, errors));
    }

    /**
     * Handles @RequestParam/@PathVariable constraint violations.
     * HTTP 400 Bad Request
     * 
     * @param ex ConstraintViolationException
     * @return ApiError with constraint violation details
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();

        log.warn("Constraint violation: {}", errors);
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(),
                        ValidationMessages.VALIDATION_ERROR, errors));
    }

    /**
     * Handles malformed JSON in request body.
     * HTTP 400 Bad Request
     * 
     * @param ex HttpMessageNotReadableException
     * @return ApiError with parse error message
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleBadJson(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(),
                        ValidationMessages.MALFORMED_JSON));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 SECURITY EXCEPTIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handles authentication failures.
     * HTTP 401 Unauthorized
     * 
     * @param ex AuthenticationException
     * @return ApiError with 401 status
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(HttpStatus.UNAUTHORIZED.value(),
                        ValidationMessages.AUTHENTICATION_REQUIRED));
    }

    /**
     * Handles Spring Security access denied (method security).
     * HTTP 403 Forbidden
     * 
     * @param ex AccessDeniedException
     * @return ApiError with 403 status
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(HttpStatus.FORBIDDEN.value(),
                        ValidationMessages.ACCESS_DENIED));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 OTHER EXCEPTIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handles ResponseStatusException (thrown by service layer).
     * 
     * @param ex ResponseStatusException
     * @return ApiError with the specified status
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : ValidationMessages.UNEXPECTED_ERROR;

        log.warn("ResponseStatusException: status={}, message={}", status, message);
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), message));
    }

    /**
     * Handles IllegalArgumentException from service layer.
     * HTTP 400 Bad Request
     * 
     * @param ex IllegalArgumentException
     * @return ApiError with 400 status
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /**
     * Catch-all handler for unexpected exceptions.
     * HTTP 500 Internal Server Error
     * 
     * Logs full stack trace for debugging but returns safe message to client.
     * 
     * @param ex Exception
     * @return ApiError with 500 status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        ValidationMessages.UNEXPECTED_ERROR));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Formats a field error into a user-friendly message.
     * 
     * @param fieldError Spring validation field error
     * @return Formatted error message
     */
    private String formatFieldError(FieldError fieldError) {
        String field = fieldError.getField();
        String code = fieldError.getCode();

        // Map common validation codes to application-specific messages
        if (code != null) {
            return switch (code.toLowerCase()) {
                case "notblank", "notnull", "notempty" -> field + " is required";
                case "size" -> field + " size is invalid";
                case "pattern" -> field + " format is invalid";
                default -> field + ": " + fieldError.getDefaultMessage();
            };
        }

        return field + ": " + fieldError.getDefaultMessage();
    }

    /**
     * Extracts service name from Feign exception for logging.
     * 
     * @param ex FeignException
     * @return Service name or "unknown"
     */
    private String extractServiceName(FeignException ex) {
        String message = ex.getMessage();
        if (message == null)
            return "unknown";

        // Try to extract service name from URL pattern
        if (message.contains("job-service") || message.contains("/jobs")) {
            return "job-service";
        } else if (message.contains("cv-service") || message.contains("/cvs")) {
            return "cv-service";
        }

        return "external-service";
    }
}
