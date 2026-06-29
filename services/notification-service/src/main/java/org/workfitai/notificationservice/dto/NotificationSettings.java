package org.workfitai.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors user-service's NotificationSettingsResponse shape (nested email/push
 * sub-objects). Must stay in sync with that DTO's field names.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettings {
    private EmailNotifications email;
    private PushNotifications push;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailNotifications {
        private Boolean jobAlerts;
        private Boolean applicationUpdates;
        private Boolean messages;
        private Boolean newsletter;
        private Boolean marketingEmails;
        private Boolean securityAlerts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PushNotifications {
        private Boolean jobAlerts;
        private Boolean applicationUpdates;
        private Boolean messages;
        private Boolean reminders;
    }

    /**
     * Per-type email gate. Unmapped template types (and missing settings)
     * default to enabled - mirrors prior fail-open behavior.
     *
     * marketing/newsletter checked by substring (not exact match) since no
     * current producer emits a templateType for these yet - keeps the toggles
     * functional the moment such a notification type is introduced, instead
     * of silently never applying.
     */
    public boolean isEmailEnabledForType(String templateType) {
        if (email == null || templateType == null) {
            return true;
        }
        String type = templateType.toLowerCase();
        if (type.contains("marketing")) {
            return email.getMarketingEmails() == null || email.getMarketingEmails();
        }
        if (type.contains("newsletter")) {
            return email.getNewsletter() == null || email.getNewsletter();
        }
        Boolean flag = resolveTypeFlag(type, email.getJobAlerts(), email.getApplicationUpdates());
        return flag == null || flag;
    }

    /**
     * Per-type push/in-app gate. Unmapped template types (and missing settings)
     * default to enabled.
     */
    public boolean isPushEnabledForType(String templateType) {
        if (push == null || templateType == null) {
            return true;
        }
        Boolean flag = resolveTypeFlag(templateType.toLowerCase(), push.getJobAlerts(), push.getApplicationUpdates());
        return flag == null || flag;
    }

    private static Boolean resolveTypeFlag(String lowerCaseTemplateType, Boolean jobAlerts, Boolean applicationUpdates) {
        switch (lowerCaseTemplateType) {
            case "job-created":
                return jobAlerts;
            case "application-confirmation":
            case "new-application-hr":
                return applicationUpdates;
            case "password-changed":
            case "password-reset":
            case "password-set":
                // Always send security-critical emails regardless of preference.
                return true;
            default:
                // No specific toggle for this type - default to enabled.
                return null;
        }
    }
}
