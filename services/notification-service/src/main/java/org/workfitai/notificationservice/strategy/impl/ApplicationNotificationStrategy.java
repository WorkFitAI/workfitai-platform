package org.workfitai.notificationservice.strategy.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.service.EmailService;
import org.workfitai.notificationservice.service.NotificationPersistenceService;
import org.workfitai.notificationservice.service.RateLimiterService;
import org.workfitai.notificationservice.strategy.NotificationStrategy;

/**
 * Strategy for application-related notifications (submitted, viewed, status
 * changed).
 * Uses rate limiting, preference-based channel selection.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationNotificationStrategy implements NotificationStrategy {

    private final EmailService emailService;
    private final NotificationPersistenceService persistenceService;
    private final RateLimiterService rateLimiterService;

    @Override
    public boolean canHandle(NotificationEvent event) {
        String type = event.getNotificationType();
        return type != null && (type.startsWith("application_") ||
                type.startsWith("APPLICATION_"));
    }

    @Override
    public boolean process(NotificationEvent event) {
        log.info("[APPLICATION] Processing application notification: {} for {}",
                event.getNotificationType(), event.getRecipientEmail());

        // Check rate limit (allow max 10 application notifications per hour)
        String rateLimitKey = "app_notif:" + event.getRecipientEmail();
        if (!rateLimiterService.allowRequest(rateLimitKey, 10, 3600)) {
            log.warn("Rate limit exceeded for application notifications: {}", event.getRecipientEmail());
            // Still create in-app notification, skip email
            try {
                persistenceService.createNotification(event);
            } catch (Exception e) {
                log.error("Failed to create in-app notification: {}", e.getMessage());
            }
            return false;
        }

        // Send based on event flags
        boolean emailSent = false;
        if (Boolean.TRUE.equals(event.getSendEmail())) {
            emailSent = emailService.sendEmail(event);
        }

        boolean inAppCreated = false;
        if (Boolean.TRUE.equals(event.getCreateInAppNotification())) {
            try {
                inAppCreated = persistenceService.createNotification(event) != null;
            } catch (Exception e) {
                log.error("Failed to create in-app notification: {}", e.getMessage());
            }
        }

        if (emailSent) {
            persistenceService.saveEmailLog(event, true, null);
        }

        return emailSent || inAppCreated;
    }

    @Override
    public int getPriority() {
        return 50; // Medium priority
    }
}
