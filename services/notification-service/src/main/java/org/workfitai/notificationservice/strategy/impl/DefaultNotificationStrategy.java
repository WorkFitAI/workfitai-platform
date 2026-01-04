package org.workfitai.notificationservice.strategy.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.workfitai.notificationservice.client.UserServiceClient;
import org.workfitai.notificationservice.dto.NotificationSettings;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.service.EmailService;
import org.workfitai.notificationservice.service.NotificationPersistenceService;
import org.workfitai.notificationservice.strategy.NotificationStrategy;

/**
 * Default fallback strategy for notifications that don't match specific
 * strategies.
 * Uses event flags (sendEmail, createInAppNotification) to determine delivery.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultNotificationStrategy implements NotificationStrategy {

    private final EmailService emailService;
    private final NotificationPersistenceService persistenceService;
    private final UserServiceClient userServiceClient;

    @Override
    public boolean canHandle(NotificationEvent event) {
        return true; // Always matches as fallback
    }

    @Override
    public boolean process(NotificationEvent event) {
        log.info("[DEFAULT] Processing notification: type={}, to={}, sendEmail={}, createInApp={}",
                event.getEventType(), event.getRecipientEmail(), event.getSendEmail(),
                event.getCreateInAppNotification());

        // ✅ Check user notification preferences
        NotificationSettings settings = userServiceClient.getNotificationSettings(event.getRecipientEmail());

        boolean emailSent = false;
        boolean inAppCreated = false;

        // Handle email sending - check if user enabled email notifications
        // Default to true if not specified (backward compatibility)
        boolean shouldSendEmail = event.getSendEmail() == null || Boolean.TRUE.equals(event.getSendEmail());

        if (shouldSendEmail) {
            if (Boolean.TRUE.equals(settings.getEmailEnabled())) {
                emailSent = emailService.sendEmail(event);
                persistenceService.saveEmailLog(event, emailSent, emailSent ? null : "delivery_failed");
            } else {
                log.info("Skipping email for {} - email notifications disabled by user", event.getRecipientEmail());
                persistenceService.saveEmailLog(event, false, "user_disabled_email_notifications");
            }
        } else {
            log.debug("Skipping email for {} - sendEmail=false in event", event.getRecipientEmail());
        }

        // Handle in-app notification creation
        // Only create if explicitly set to true (to avoid creating notifications for
        // transactional emails)
        if (Boolean.TRUE.equals(event.getCreateInAppNotification())) {
            if (Boolean.TRUE.equals(settings.getPushEnabled())) {
                try {
                    inAppCreated = persistenceService.createNotification(event) != null;
                    log.debug("Created in-app notification for {}", event.getRecipientEmail());
                } catch (Exception e) {
                    log.error("Failed to create in-app notification: {}", e.getMessage());
                }
            } else {
                log.info("Skipping in-app notification for {} - push notifications disabled by user",
                        event.getRecipientEmail());
            }
        } else {
            log.debug("Skipping in-app notification for {} - createInAppNotification={}",
                    event.getRecipientEmail(), event.getCreateInAppNotification());
        }

        return emailSent || inAppCreated;
    }

    @Override
    public int getPriority() {
        return 1000; // Lowest priority (fallback)
    }
}
