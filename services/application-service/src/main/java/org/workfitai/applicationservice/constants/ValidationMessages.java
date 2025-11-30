package org.workfitai.applicationservice.constants;

/**
 * Centralized constants for validation and error messages.
 * 
 * Benefits:
 * - Single source of truth for all user-facing messages
 * - Easy to update messages without searching codebase
 * - Supports future i18n/localization
 * - Consistent messaging across the application
 * 
 * Naming convention:
 * - ENTITY_FIELD_ERROR for validation errors
 * - ENTITY_ACTION_ERROR for business rule violations
 */
public final class ValidationMessages {

    // Private constructor to prevent instantiation
    private ValidationMessages() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // ==================== Application Field Validation ====================

    public static final String APPLICATION_USER_ID_REQUIRED = "User ID is required";
    public static final String APPLICATION_JOB_ID_REQUIRED = "Job ID is required";
    public static final String APPLICATION_CV_ID_REQUIRED = "CV ID is required";
    public static final String APPLICATION_STATUS_REQUIRED = "Application status is required";

    // ==================== Application Business Rules ====================

    /**
     * Error when user tries to apply to the same job twice.
     * HTTP 409 Conflict
     */
    public static final String APPLICATION_ALREADY_EXISTS = "You have already applied to this job";

    /**
     * Error when job is not in PUBLISHED status.
     * HTTP 403 Forbidden
     */
    public static final String JOB_NOT_PUBLISHED = "Cannot apply to a job that is not published";

    /**
     * Error when CV doesn't belong to the applying user.
     * HTTP 403 Forbidden
     */
    public static final String CV_NOT_OWNED_BY_USER = "The CV does not belong to this user";

    /**
     * Error when application is not found by ID.
     * HTTP 404 Not Found
     */
    public static final String APPLICATION_NOT_FOUND = "Application not found";

    // ==================== Cross-Service Errors ====================

    /**
     * Error when job-service returns 404.
     */
    public static final String JOB_NOT_FOUND = "Job not found";

    /**
     * Error when cv-service returns 404.
     */
    public static final String CV_NOT_FOUND = "CV not found";

    /**
     * Error when a cross-service call fails unexpectedly.
     */
    public static final String SERVICE_UNAVAILABLE = "Service temporarily unavailable. Please try again later.";

    // ==================== General Validation ====================

    public static final String VALIDATION_ERROR = "Validation failed. Please check the provided data.";
    public static final String INVALID_REQUEST = "Invalid request. Please check your input.";
    public static final String MALFORMED_JSON = "Invalid JSON format in request body";
    public static final String UNEXPECTED_ERROR = "An unexpected error occurred. Please try again later.";

    // ==================== Security/Authentication ====================

    public static final String AUTHENTICATION_REQUIRED = "Authentication is required to access this resource";
    public static final String ACCESS_DENIED = "You don't have permission to perform this action";
    public static final String UNAUTHORIZED = "Access denied. Please authenticate.";
    public static final String FORBIDDEN = "You don't have permission to perform this action.";
    public static final String INTERNAL_ERROR = "An unexpected error occurred. Please try again later.";

    // ==================== Pagination ====================

    public static final String INVALID_PAGE_NUMBER = "Page number must be 0 or greater";
    public static final String INVALID_PAGE_SIZE = "Page size must be between 1 and 100";
}
