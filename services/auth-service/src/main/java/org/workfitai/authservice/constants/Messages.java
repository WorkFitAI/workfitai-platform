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
        public static final String OTP_INVALID = "Invalid or expired OTP";
        public static final String ACCOUNT_NOT_ACTIVE = "Account is not active";
        public static final String ACCOUNT_WAITING_APPROVAL = "Account is waiting for approval";
        public static final String ADMIN_SELF_REGISTER_FORBIDDEN = "Admin accounts cannot be self-registered";
        public static final String HR_PROFILE_REQUIRED = "HR profile is required for HR registrations";
        public static final String COMPANY_DETAILS_REQUIRED = "Company details are required for HR manager registrations";
        public static final String COMPANY_ID_MISMATCH = "Company ID on HR profile must match company details";

        // Role & permission domain errors
        public static final String ROLE_ALREADY_EXISTS = "Role already exists: %s";
        public static final String PERMISSION_ALREADY_EXISTS = "Permission already exists: %s";
        public static final String PERMISSION_ALREADY_ASSIGNED = "Permission already assigned to role";
        public static final String PERMISSION_IN_USE = "Permission is referenced by one or more roles";
        public static final String UNKNOWN_PERMISSION = "Unknown permission: %s";
        public static final String ROLE_NOT_FOUND = "Role not found";
        public static final String PERMISSION_NOT_FOUND = "Permission not found";
        public static final String UNKNOWN_ROLE = "Unknown role: %s";
    }

    // Success Messages
    public static final class Success {
        public static final String AUTH_SERVICE_RUNNING = "Auth Service is running";
        public static final String OPERATION_SUCCESS = "Success";
        public static final String PERMISSION_CREATED = "Permission created";
        public static final String PERMISSIONS_CREATED_BATCH = "Permissions created in batch";
        public static final String PERMISSION_UPDATED = "Permission updated";
        public static final String PERMISSION_DELETED = "Permission deleted";
        public static final String PERMISSIONS_FETCHED = "Permissions retrieved";
        public static final String ROLE_CREATED = "Role created";
        public static final String ROLES_CREATED_BATCH = "Roles created in batch";
        public static final String ROLE_UPDATED = "Role updated";
        public static final String ROLE_DELETED = "Role deleted";
        public static final String ROLES_FETCHED = "Roles retrieved";
        public static final String ROLE_PERMISSION_ADDED = "Permission assigned to role";
        public static final String ROLE_PERMISSIONS_ADDED_BATCH = "Permissions assigned to role in batch";
        public static final String ROLE_PERMISSION_REMOVED = "Permission removed from role";
        public static final String ROLE_PERMISSIONS_REMOVED_BATCH = "Permissions removed from role in batch";
        public static final String ROLE_GRANTED = "Role granted";
        public static final String ROLE_REVOKED = "Role revoked";
        public static final String USER_ROLES_FETCHED = "User roles retrieved";
        public static final String TOKENS_ISSUED = "Tokens issued";
        public static final String LOGGED_OUT = "Logged out";
        public static final String TOKENS_REFRESHED = "Tokens refreshed";
        public static final String USER_REGISTERED = "User registered";
        public static final String OTP_SENT = "OTP sent";
        public static final String ACCOUNT_VERIFIED = "Account verified";
    }

    public static final class Validation {
        public static final String PERMISSION_NAME_REQUIRED = "Permission code must not be blank";
        public static final String PERMISSION_NAME_INVALID = "Permission code must follow domain:action format";
        public static final String PERMISSION_NAME_SIZE = "Permission code must not exceed 64 characters";
        public static final String PERMISSION_DESCRIPTION_SIZE = "Permission description must not exceed 255 characters";

        public static final String ROLE_NAME_REQUIRED = "Role name must not be blank";
        public static final String ROLE_NAME_SIZE = "Role name must not exceed 64 characters";
        public static final String ROLE_DESCRIPTION_SIZE = "Role description must not exceed 255 characters";
        public static final String ROLE_PERMISSIONS_INVALID = "Role permissions must not contain blank values";
        public static final String PERMISSION_CODE_REQUIRED = "Permission code must not be blank";

        public static final String USERNAME_REQUIRED = "Username must not be blank";
        public static final String PASSWORD_REQUIRED = "Password must not be blank";
        public static final String PASSWORD_LENGTH = "Password length must be between {min} and {max} characters";
        public static final String EMAIL_REQUIRED = "Email must not be blank";
        public static final String EMAIL_INVALID = "Email must be valid";
        public static final String FULLNAME_REQUIRED = "Full name must not be blank";
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
