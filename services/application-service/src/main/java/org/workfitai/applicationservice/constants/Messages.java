package org.workfitai.applicationservice.constants;

/**
 * Centralized message constants for the application service.
 * Supports future i18n/localization by keeping all user-facing strings in one
 * place.
 */
public final class Messages {

    private Messages() {
    }

    public static final class Success {
        private Success() {
        }

        public static final String APPLICATION_CREATED = "Application submitted successfully";
        public static final String APPLICATION_UPDATED = "Application updated successfully";
        public static final String APPLICATION_WITHDRAWN = "Application withdrawn successfully";
        public static final String STATUS_UPDATED = "Application status updated successfully";
        public static final String APPLICATION_FOUND = "Application retrieved successfully";
        public static final String APPLICATIONS_FOUND = "Applications retrieved successfully";
    }

    public static final class Error {
        private Error() {
        }

        public static final String APPLICATION_NOT_FOUND = "Application not found";
        public static final String APPLICATION_ALREADY_EXISTS = "You have already applied to this job";
        public static final String INVALID_STATUS_TRANSITION = "Invalid status transition from %s to %s";
        public static final String ACCESS_DENIED = "You don't have permission to perform this action";
        public static final String AUTHENTICATION_REQUIRED = "Authentication is required to access this resource";
        public static final String UNAUTHORIZED = "Access denied. Please authenticate";
        public static final String FORBIDDEN = "You don't have permission to access this resource";
        public static final String VALIDATION_ERROR = "Validation failed. Please check the provided data";
        public static final String INVALID_REQUEST = "Invalid request. Please check your input";
        public static final String MALFORMED_JSON = "Invalid JSON format in request body";
        public static final String UNEXPECTED_ERROR = "An unexpected error occurred. Please try again later";
        public static final String INVALID_PAGE_NUMBER = "Page number must be 0 or greater";
        public static final String INVALID_PAGE_SIZE = "Page size must be between 1 and 100";
    }

    public static final class Validation {
        private Validation() {
        }

        public static final String USERNAME_REQUIRED = "Username is required";
        public static final String JOB_ID_REQUIRED = "Job ID is required";
        public static final String CV_ID_REQUIRED = "CV ID is required";
        public static final String STATUS_REQUIRED = "Application status is required";
        public static final String NOTE_MAX_LENGTH = "Note cannot exceed 2000 characters";
        public static final String JOB_ID_FORMAT = "Job ID must be a valid UUID format";
        public static final String CV_ID_FORMAT = "CV ID must be a valid format";
    }

    public static final class Kafka {
        private Kafka() {
        }

        public static final String TOPIC_APPLICATION_EVENTS = "application-events";
        public static final String TOPIC_APPLICATION_STATUS = "application-status";
        public static final String EVENT_APPLICATION_CREATED = "APPLICATION_CREATED";
        public static final String EVENT_APPLICATION_WITHDRAWN = "APPLICATION_WITHDRAWN";
        public static final String EVENT_STATUS_CHANGED = "STATUS_CHANGED";
        public static final String PUBLISH_SUCCESS = "Successfully published {} event for application {}";
        public static final String PUBLISH_FAILED = "Failed to publish {} event for application {}: {}";
    }

    public static final class Log {
        private Log() {
        }

        public static final String CREATING_APPLICATION = "Creating application for user {} to job {}";
        public static final String APPLICATION_CREATED = "Application created successfully: id={}";
        public static final String FETCHING_APPLICATION = "Fetching application by id: {}";
        public static final String FETCHING_USER_APPLICATIONS = "Fetching applications for user: {}";
        public static final String UPDATING_STATUS = "Updating application {} status to {}";
        public static final String STATUS_UPDATED = "Application {} status updated to {}";
        public static final String WITHDRAWING_APPLICATION = "User {} withdrawing application {}";
        public static final String APPLICATION_WITHDRAWN = "Application {} withdrawn successfully";
        public static final String DUPLICATE_APPLICATION = "Duplicate application attempt: user {} already applied to job {}";
        public static final String KAFKA_SEND_SUCCESS = "Successfully sent {} event for application {} to topic {} at offset {}";
        public static final String KAFKA_SEND_FAILED = "Failed to send {} event for application {}: {}";
    }

    public static final class Api {
        private Api() {
        }

        public static final String TAG_NAME = "Applications";
        public static final String TAG_DESCRIPTION = "Job application management endpoints";
        public static final String CREATE_SUMMARY = "Submit a job application";
        public static final String CREATE_DESCRIPTION = "Apply to a job with your CV. An event will be published to notify other services.";
        public static final String CREATE_201 = "Application created successfully";
        public static final String CREATE_400 = "Validation error";
        public static final String CREATE_409 = "Already applied to this job";
        public static final String GET_MY_SUMMARY = "Get my applications";
        public static final String GET_MY_DESCRIPTION = "Retrieve your job applications with optional status filter";
        public static final String GET_BY_ID_SUMMARY = "Get application by ID";
        public static final String GET_BY_ID_DESCRIPTION = "Retrieve application details. Requires ownership or HR/ADMIN role.";
        public static final String GET_200 = "Application found";
        public static final String GET_403 = "Not authorized to view";
        public static final String GET_404 = "Application not found";
        public static final String GET_BY_JOB_SUMMARY = "Get applications for a job";
        public static final String GET_BY_JOB_DESCRIPTION = "Retrieve all applications for a specific job. HR/ADMIN only.";
        public static final String UPDATE_STATUS_SUMMARY = "Update application status";
        public static final String UPDATE_STATUS_DESCRIPTION = "Change application status (e.g., REVIEWING, INTERVIEW). HR/ADMIN only. Publishes status change event.";
        public static final String UPDATE_200 = "Status updated";
        public static final String UPDATE_400 = "Invalid status transition";
        public static final String WITHDRAW_SUMMARY = "Withdraw application";
        public static final String WITHDRAW_DESCRIPTION = "Cancel your job application. Only the applicant can withdraw. Publishes withdrawal event.";
        public static final String WITHDRAW_204 = "Application withdrawn";
        public static final String WITHDRAW_403 = "Not your application";
        public static final String CHECK_SUMMARY = "Check if already applied";
        public static final String CHECK_DESCRIPTION = "Check if current user has already applied to a specific job";
        public static final String COUNT_MY_SUMMARY = "Get my application count";
        public static final String COUNT_JOB_SUMMARY = "Get applicant count for a job";
        public static final String PARAM_STATUS = "Filter by application status";
        public static final String PARAM_PAGE = "Page number (0-indexed)";
        public static final String PARAM_SIZE = "Page size (max 100)";
        public static final String PARAM_JOB_ID = "Job ID to filter by";
    }
}
