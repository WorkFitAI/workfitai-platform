package org.workfitai.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.workfitai.notificationservice.dto.NotificationSettings;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;

/**
 * Service to check user notification settings before sending emails.
 * Calls user-service's internal API to fetch user preferences.
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

        // If no recipientEmail, cannot check preferences
        if (event.getRecipientEmail() == null && event.getRecipientUserId() == null) {
            log.warn("Cannot check preferences - no recipient identifier");
            return false;
        }

        try {
            // Internal-only endpoint (not exposed through API Gateway), no auth required
            String endpoint = userServiceUrl + "/api/v1/internal/notification-settings/{email}";

            NotificationSettings settings = restTemplate.getForObject(
                    endpoint, NotificationSettings.class, event.getRecipientEmail());

            if (settings == null) {
                log.warn("No notification settings found for user: {}", event.getRecipientEmail());
                return true; // Default to sending if settings not found
            }

            boolean enabled = settings.isEmailEnabledForType(event.getTemplateType());
            if (!enabled) {
                log.info("User {} has disabled {} email notifications",
                        event.getRecipientEmail(), event.getTemplateType());
            }
            return enabled;

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
}
