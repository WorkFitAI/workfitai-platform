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

        NotificationSettings settings = userServiceClient.getNotificationSettings(event.getRecipientEmail());

        boolean emailSent = false;
        boolean inAppCreated = false;

        // Handle email sending - actual per-type preference gating happens inside
        // EmailService.sendEmail() (via UserPreferenceService), which every
        // strategy delegates to. Deliberately NOT duplicated here: a coarse
        // emailEnabled check on this strategy's own settings fetch would either
        // diverge from EmailService's per-type result or double-fetch settings.
        // Default to true if not specified (backward compatibility)
        boolean shouldSendEmail = event.getSendEmail() == null || Boolean.TRUE.equals(event.getSendEmail());

        if (shouldSendEmail) {
            emailSent = emailService.sendEmail(event);
            persistenceService.saveEmailLog(event, emailSent, emailSent ? null : "delivery_failed");
        } else {
            log.debug("Skipping email for {} - sendEmail=false in event", event.getRecipientEmail());
        }

        // Handle in-app notification creation
        // Only create if explicitly set to true (to avoid creating notifications for
        // transactional emails)
        if (Boolean.TRUE.equals(event.getCreateInAppNotification())) {
            if (settings.isPushEnabledForType(event.getTemplateType())) {
                try {
                    inAppCreated = persistenceService.createNotification(event) != null;
                    log.debug("Created in-app notification for {}", event.getRecipientEmail());
                } catch (Exception e) {
                    log.error("Failed to create in-app notification: {}", e.getMessage());
                }
            } else {
                log.info("Skipping in-app notification for {} - push disabled for type {}",
                        event.getRecipientEmail(), event.getTemplateType());
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
