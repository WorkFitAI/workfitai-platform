package org.workfitai.monitoringservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Simplified DTO for user activity monitoring dashboard.
 * Shows what users are doing in the system without technical details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserActivityEntry {

    /**
     * Unique activity ID
     */
    private String id;

    /**
     * When the activity occurred
     */
    private Instant timestamp;

    /**
     * Username who performed the action
     */
    private String username;

    /**
     * User roles at the time of action
     */
    private String roles;

    /**
     * Which service handled the request
     */
    private String service;

    /**
     * HTTP method (GET, POST, PUT, DELETE)
     */
    private String method;

    /**
     * Request path (e.g., /api/jobs, /auth/login)
     */
    private String path;

    /**
     * Request ID for tracing
     */
    private String requestId;

    /**
     * Log level (INFO, WARN, ERROR)
     */
    private String level;

    /**
     * Whether this was an error
     */
    private Boolean isError;

    /**
     * Log type classification (USER_ACTION, SYSTEM, etc.)
     */
    private String logType;

    /**
     * Action code (e.g., CREATE_JOB, UPDATE_PROFILE)
     */
    private String action;

    /**
     * Entity type being operated on (e.g., Job, User, Application)
     */
    private String entityType;

    /**
     * Entity ID being operated on
     */
    private String entityId;

    /**
     * Log message / description (technical)
     */
    private String message;

    /**
     * Human-readable Vietnamese message for end users
     * Example: "Đăng nhập vào hệ thống", "Tạo tin tuyển dụng mới"
     */
    private String displayMessage;

    /**
     * Relative time description (e.g., "5 phút trước")
     */
    private String relativeTime;

    /**
     * Icon/emoji representing the action
     */
    private String icon;
}
