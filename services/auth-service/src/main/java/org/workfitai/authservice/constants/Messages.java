package org.workfitai.authservice.constants;

/**
 * Constants for error and success messages used throughout the auth service.
 */
public final class Messages {

    private Messages() {
        // Prevent instantiation
    }

    // Error Messages
    public static final class Error {
        public static final String USERNAME_EMAIL_ALREADY_IN_USE = "Username or email already in use";
        public static final String VALIDATION_FAILED = "Validation failed";
        public static final String INVALID_REQUEST = "Invalid request";
        public static final String MALFORMED_JSON = "Malformed JSON";
        public static final String UNAUTHORIZED = "Unauthorized";
        public static final String FORBIDDEN = "Forbidden";
        public static final String INTERNAL_SERVER_ERROR = "Internal server error";
        public static final String INVALID_CREDENTIALS = "Invalid credentials";
        public static final String USER_NOT_FOUND = "User not found";
        public static final String TOKEN_INVALID = "TOKEN_INVALID";
        public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
        public static final String DEFAULT_ERROR = "Error";
        public static final String UNEXPECTED_ERROR = "Unexpected error";
        public static final String INVALID_SECRET_PROVIDED = "Invalid secret provided";

        // Role related errors
        public static final String ROLE_EXISTS = "Role exists";
        public static final String UNKNOWN_PERMISSION = "Unknown perm: ";
        public static final String ROLE_NOT_FOUND = "Role not found";
        public static final String PERMISSION_NOT_FOUND = "Permission not found";
        public static final String UNKNOWN_ROLE = "Unknown role: ";
    }

    // Success Messages
    public static final class Success {
        public static final String AUTH_SERVICE_RUNNING = "Auth Service is running";
    }

    // JWT Claims
    public static final class JWT {
        public static final String ISSUER = "auth-service";
        public static final String ROLES_CLAIM = "roles";
        public static final String PERMISSIONS_CLAIM = "perms";
    }

    // Miscellaneous
    public static final class Misc {
        public static final String DEFAULT_DEVICE = "default";
        public static final String REFRESH_TOKEN_COOKIE_NAME = "RT";
    }
}
