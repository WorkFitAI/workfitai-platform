package org.workfitai.notificationservice.enums;

/**
 * Delivery channels for notifications.
 */
public enum NotificationChannel {
    /**
     * Both email and in-app notification.
     * Used for critical or important events.
     */
    BOTH,

    /**
     * Email only.
     * Used for transactional emails (OTP, password reset).
     */
    EMAIL_ONLY,

    /**
     * In-app notification only.
     * Used for low-priority system updates.
     */
    IN_APP_ONLY,

    /**
     * Auto-determine based on user preferences and notification type.
     */
    AUTO;

    public boolean shouldSendEmail() {
        return this == BOTH || this == EMAIL_ONLY;
    }

    public boolean shouldCreateInApp() {
        return this == BOTH || this == IN_APP_ONLY;
    }
}
