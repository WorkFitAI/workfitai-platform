package org.workfitai.notificationservice.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Types of in-app notifications.
 */
public enum NotificationType {
    // Auth & Account
    ACCOUNT_ACTIVATED("account_activated"),
    ACCOUNT_APPROVED("account_approved"),
    ACCOUNT_PENDING_APPROVAL("account_pending_approval"),
    PASSWORD_CHANGED("password_changed"),

    // Application related (for future use)
    APPLICATION_SUBMITTED("application_submitted"),
    APPLICATION_VIEWED("application_viewed"),
    APPLICATION_STATUS_CHANGED("application_status_changed"),
    APPLICATION_ACCEPTED("application_accepted"),
    APPLICATION_REJECTED("application_rejected"),

    // Job related (for future use)
    JOB_POSTED("job_posted"),
    JOB_EXPIRED("job_expired"),
    JOB_MATCHING("job_matching"),
    NEW_APPLICANT("new_applicant"),

    // CV related (for future use)
    CV_VIEWED("cv_viewed"),
    CV_DOWNLOADED("cv_downloaded"),
    CV_ANALYSIS_COMPLETE("cv_analysis_complete"),

    // System
    SYSTEM_ANNOUNCEMENT("system_announcement"),
    GENERAL("general");

    private final String value;

    NotificationType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static NotificationType fromValue(String value) {
        for (NotificationType type : NotificationType.values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return GENERAL;
    }
}
