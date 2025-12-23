package org.workfitai.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;

import java.util.Map;

/**
 * Service to check user notification and privacy settings before sending
 * emails.
 * Calls user-service to fetch user preferences.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPreferenceService {

    private final RestTemplate restTemplate;

    @Value("${app.services.user-service.url:http://user-service:9081}")
    private String userServiceUrl;

    /**
     * Check if user allows this type of notification based on their settings.
     * 
     * @param event The notification event
     * @return true if email should be sent, false otherwise
     */
    public boolean shouldSendNotification(NotificationEvent event) {
        // For critical notifications (security, auth), always send
        if (isCriticalNotification(event)) {
            log.debug("Critical notification - bypassing preference check");
            return true;
        }

        // If no recipientEmail or recipientUserId, cannot check preferences
        if (event.getRecipientEmail() == null && event.getRecipientUserId() == null) {
            log.warn("Cannot check preferences - no recipient identifier");
            return false;
        }

        try {
            // Call user-service to get notification settings
            String endpoint = String.format("%s/api/v1/profile/notification-settings", userServiceUrl);

            // TODO: Add proper authentication header (service-to-service token)
            // For now, we'll use recipientEmail as identifier
            Map<String, Object> settings = restTemplate.getForObject(
                    endpoint + "?email=" + event.getRecipientEmail(),
                    Map.class);

            if (settings == null) {
                log.warn("No notification settings found for user: {}", event.getRecipientEmail());
                return true; // Default to sending if settings not found
            }

            // Check email notifications enabled
            Boolean emailNotificationsEnabled = (Boolean) settings.get("emailNotificationsEnabled");
            if (Boolean.FALSE.equals(emailNotificationsEnabled)) {
                log.info("User {} has disabled email notifications", event.getRecipientEmail());
                return false;
            }

            // Check specific notification type preferences
            String notificationType = event.getTemplateType();
            if (notificationType != null) {
                Boolean typeEnabled = isNotificationTypeEnabled(settings, notificationType);
                if (Boolean.FALSE.equals(typeEnabled)) {
                    log.info("User {} has disabled {} notifications",
                            event.getRecipientEmail(), notificationType);
                    return false;
                }
            }

            // Check privacy settings
            return checkPrivacySettings(event, settings);

        } catch (Exception e) {
            log.error("Failed to check user preferences for {}: {}",
                    event.getRecipientEmail(), e.getMessage());
            // Default to sending on error (fail-open for non-critical)
            return true;
        }
    }

    /**
     * Check if this is a critical notification that should bypass user preferences
     */
    private boolean isCriticalNotification(NotificationEvent event) {
        if (event.getEventType() == null) {
            return false;
        }

        String eventType = event.getEventType().toUpperCase();
        return eventType.contains("PASSWORD") ||
                eventType.contains("SECURITY") ||
                eventType.contains("2FA") ||
                eventType.contains("LOGIN") ||
                eventType.contains("OTP") ||
                eventType.contains("APPROVAL") ||
                eventType.equals("ACCOUNT_ACTIVATED") ||
                eventType.equals("ACCOUNT_DEACTIVATED");
    }

    /**
     * Check if specific notification type is enabled in user settings
     */
    private Boolean isNotificationTypeEnabled(Map<String, Object> settings, String templateType) {
        // Map template types to setting keys
        switch (templateType.toLowerCase()) {
            case "job-created":
                return (Boolean) settings.getOrDefault("jobAlertsEnabled", true);

            case "cv-upload-success":
            case "cv-parsed":
                return (Boolean) settings.getOrDefault("cvUpdatesEnabled", true);

            case "application-confirmation":
            case "new-application-hr":
                return (Boolean) settings.getOrDefault("applicationUpdatesEnabled", true);

            case "password-changed":
            case "password-reset":
            case "password-set":
                // Always send password-related emails (security)
                return true;

            default:
                // Unknown type - default to enabled
                return true;
        }
    }

    /**
     * Check privacy settings to determine if notification should be sent
     */
    private boolean checkPrivacySettings(NotificationEvent event, Map<String, Object> settings) {
        // Check if user has set profile to private
        Boolean profilePublic = (Boolean) settings.get("profilePublic");
        if (Boolean.FALSE.equals(profilePublic)) {
            // For job recommendations/matches, respect privacy
            if (event.getTemplateType() != null &&
                    event.getTemplateType().contains("job-match")) {
                log.info("User has private profile - skipping job match notification");
                return false;
            }
        }

        // Check data sharing preferences for marketing emails
        Boolean allowMarketingEmails = (Boolean) settings.get("allowMarketingEmails");
        if (Boolean.FALSE.equals(allowMarketingEmails)) {
            if (event.getTemplateType() != null &&
                    event.getTemplateType().contains("marketing")) {
                log.info("User opted out of marketing emails");
                return false;
            }
        }

        return true;
    }
}
