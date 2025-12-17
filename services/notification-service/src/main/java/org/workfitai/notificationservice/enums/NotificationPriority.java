package org.workfitai.notificationservice.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Priority levels for notifications.
 * Higher priority notifications are processed first and bypass rate limiting.
 */
public enum NotificationPriority {
    /**
     * CRITICAL - Security alerts, password changes, account lockouts.
     * Always sent immediately, no rate limiting, both email + in-app.
     */
    CRITICAL("critical", 1),

    /**
     * HIGH - Important account events (approval, rejection, login alerts).
     * Minimal rate limiting, both email + in-app.
     */
    HIGH("high", 2),

    /**
     * MEDIUM - Regular notifications (application updates, job matches).
     * Standard rate limiting, email OR in-app based on settings.
     */
    MEDIUM("medium", 3),

    /**
     * LOW - Informational updates, tips, recommendations.
     * Heavy rate limiting, batching enabled, in-app only by default.
     */
    LOW("low", 4);

    private final String value;
    private final int level;

    NotificationPriority(String value, int level) {
        this.value = value;
        this.level = level;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public int getLevel() {
        return level;
    }

    public boolean isCritical() {
        return this == CRITICAL;
    }

    public boolean shouldBypassRateLimit() {
        return this == CRITICAL || this == HIGH;
    }

    @JsonCreator
    public static NotificationPriority fromValue(String value) {
        if (value == null)
            return MEDIUM;
        for (NotificationPriority priority : NotificationPriority.values()) {
            if (priority.value.equalsIgnoreCase(value)) {
                return priority;
            }
        }
        return MEDIUM;
    }
}
