package org.workfitai.applicationservice.constants;

/**
 * @deprecated Use {@link Messages} instead. Kept for backward compatibility.
 */
@Deprecated(since = "1.0", forRemoval = false)
public final class ValidationMessages {

    private ValidationMessages() {
    }

    // Application Field Validation
    public static final String APPLICATION_USER_ID_REQUIRED = Messages.Validation.USERNAME_REQUIRED;
    public static final String APPLICATION_JOB_ID_REQUIRED = Messages.Validation.JOB_ID_REQUIRED;
    public static final String APPLICATION_CV_ID_REQUIRED = Messages.Validation.CV_ID_REQUIRED;
    public static final String APPLICATION_STATUS_REQUIRED = Messages.Validation.STATUS_REQUIRED;

    // Application Business Rules
    public static final String APPLICATION_ALREADY_EXISTS = Messages.Error.APPLICATION_ALREADY_EXISTS;
    public static final String APPLICATION_NOT_FOUND = Messages.Error.APPLICATION_NOT_FOUND;

    // General Validation
    public static final String VALIDATION_ERROR = Messages.Error.VALIDATION_ERROR;
    public static final String INVALID_REQUEST = Messages.Error.INVALID_REQUEST;
    public static final String MALFORMED_JSON = Messages.Error.MALFORMED_JSON;
    public static final String UNEXPECTED_ERROR = Messages.Error.UNEXPECTED_ERROR;

    // Security/Authentication
    public static final String AUTHENTICATION_REQUIRED = Messages.Error.AUTHENTICATION_REQUIRED;
    public static final String ACCESS_DENIED = Messages.Error.ACCESS_DENIED;
    public static final String UNAUTHORIZED = Messages.Error.UNAUTHORIZED;
    public static final String FORBIDDEN = Messages.Error.FORBIDDEN;
    public static final String INTERNAL_ERROR = Messages.Error.UNEXPECTED_ERROR;

    // Pagination
    public static final String INVALID_PAGE_NUMBER = Messages.Error.INVALID_PAGE_NUMBER;
    public static final String INVALID_PAGE_SIZE = Messages.Error.INVALID_PAGE_SIZE;
}
