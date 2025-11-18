package org.workfitai.userservice.constants;

public final class ValidationMessages {

    private ValidationMessages() {
    } // Prevent instantiation

    // General validation messages
    public static final String VALIDATION_ERROR_GENERAL = "Validation failed. Please check the provided data.";
    public static final String INVALID_REQUEST_PARAMETERS = "Invalid request parameters. Please check your input.";
    public static final String MALFORMED_JSON = "Invalid JSON format. Please check your request body.";
    public static final String UNAUTHORIZED_ACCESS = "Access denied. Please check your credentials.";
    public static final String UNEXPECTED_ERROR = "An unexpected error occurred. Please try again later.";

    // Database error messages
    public static final String DATABASE_ERROR_GENERAL = "Database operation failed";
    public static final String DATABASE_DUPLICATE_DATA = "Data already exists. Please check for duplicates.";
    public static final String DATABASE_FOREIGN_KEY_ERROR = "Referenced data not found. Please check related records.";
    public static final String DATABASE_NOT_NULL_ERROR = "Required field is missing. Please provide all necessary data.";
    public static final String HIBERNATE_VALIDATION_ERROR = "Validation failed. Please check the provided data.";

    // Candidate required field messages
    public static final String CANDIDATE_FULLNAME_REQUIRED = "Full name is required";
    public static final String CANDIDATE_EMAIL_REQUIRED = "Email is required";
    public static final String CANDIDATE_PHONENUMBER_REQUIRED = "Phone number is required";
    public static final String CANDIDATE_BIRTHDAY_REQUIRED = "Birthday is required";
    public static final String CANDIDATE_ADDRESS_REQUIRED = "Address is required";

    // Candidate format validation messages
    public static final String CANDIDATE_EMAIL_INVALID = "Email format is invalid";
    public static final String CANDIDATE_PHONENUMBER_INVALID = "Phone number must be 10 digits or start with +84";
    public static final String CANDIDATE_FULLNAME_SIZE = "Full name must be between 2 and 100 characters";

    // Candidate business rule messages
    public static final String CANDIDATE_EMAIL_DUPLICATE = "Email already exists in the system";
    public static final String CANDIDATE_PHONENUMBER_DUPLICATE = "Phone number already exists in the system";
    public static final String CANDIDATE_AGE_INVALID = "Candidate must be at least 18 years old";
    public static final String CANDIDATE_BIRTHDAY_FUTURE = "Birthday cannot be in the future";

    public static final class Candidate {
        // Required field messages
        public static final String EMAIL_REQUIRED = "Email is required";
        public static final String PHONE_REQUIRED = "Phone number is required";
        public static final String PASSWORD_REQUIRED = "Password is required";
        public static final String FULL_NAME_REQUIRED = "Full name is required";

        // Format validation messages
        public static final String EMAIL_INVALID = "Email format is invalid";
        public static final String PHONE_INVALID = "Phone number must be 10 digits or start with +84";
        public static final String PASSWORD_TOO_SHORT = "Password must be at least 6 characters";

        // Length validation messages
        public static final String FULL_NAME_TOO_LONG = "Full name cannot exceed 100 characters";
        public static final String CAREER_OBJECTIVE_TOO_LONG = "Career objective cannot exceed 500 characters";
        public static final String SUMMARY_TOO_LONG = "Summary cannot exceed 1000 characters";

        private Candidate() {
        }
    }

    public static final class Experience {
        public static final String INVALID_RANGE = "Total experience must be between 0 and 50 years";

        private Experience() {
        }
    }

    public static final class General {
        public static final String VALIDATION_FAILED = "Validation failed";
        public static final String INVALID_INPUT = "Invalid input provided";

        private General() {
        }
    }

    // Test data constants for unit testing
    public static final String TEST_VALID_EMAIL = "test@example.com";
    public static final String TEST_INVALID_EMAIL = "invalid-email";
    public static final String TEST_VALID_PHONE = "0123456789";
    public static final String TEST_INVALID_PHONE = "123";
    public static final String TEST_VALID_FULLNAME = "John Doe";
    public static final String TEST_INVALID_FULLNAME_SHORT = "A";
    public static final String TEST_INVALID_FULLNAME_LONG = "A".repeat(101);

    // Common validation patterns (for reference in tests)
    public static final String PHONE_NUMBER_PATTERN = "^0\\d{9}$";
    public static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
}