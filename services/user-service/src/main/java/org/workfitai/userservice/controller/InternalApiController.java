package org.workfitai.userservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.dto.response.NotificationSettingsResponse;
import org.workfitai.userservice.service.NotificationSettingsService;

/**
 * Internal API endpoints for inter-service communication.
 * NOT exposed through API Gateway - only accessible within the cluster.
 */
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalApiController {

    private final NotificationSettingsService notificationSettingsService;

    /**
     * Get notification settings by email (for notification-service)
     */
    @GetMapping("/notification-settings/{email}")
    public ResponseEntity<NotificationSettingsResponse> getNotificationSettingsByEmail(
            @PathVariable String email) {
        log.debug("Internal API: Getting notification settings for email: {}", email);

        try {
            NotificationSettingsResponse settings = notificationSettingsService.getNotificationSettingsByEmail(email);
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            log.warn("Failed to get notification settings for email {}: {}", email, e.getMessage());
            // Return default settings (all enabled) on error
            return ResponseEntity.ok(NotificationSettingsResponse.builder()
                    .email(NotificationSettingsResponse.EmailNotifications.builder()
                            .jobAlerts(true)
                            .applicationUpdates(true)
                            .messages(true)
                            .newsletter(false)
                            .marketingEmails(false)
                            .securityAlerts(true)
                            .build())
                    .push(NotificationSettingsResponse.PushNotifications.builder()
                            .jobAlerts(true)
                            .applicationUpdates(true)
                            .messages(true)
                            .reminders(true)
                            .build())
                    .sms(NotificationSettingsResponse.SmsNotifications.builder()
                            .jobAlerts(false)
                            .securityAlerts(true)
                            .importantUpdates(true)
                            .build())
                    .build());
        }
    }
}
