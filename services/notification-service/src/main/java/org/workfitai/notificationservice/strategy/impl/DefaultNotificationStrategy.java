package org.workfitai.notificationservice.strategy.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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

    @Override
    public boolean canHandle(NotificationEvent event) {
        return true; // Always matches as fallback
    }

    @Override
    public boolean process(NotificationEvent event) {
        log.info("[DEFAULT] Processing notification: type={}, to={}",
                event.getEventType(), event.getRecipientEmail());

        boolean emailSent = false;
        boolean inAppCreated = false;

        // Handle email sending
        if (Boolean.TRUE.equals(event.getSendEmail()) || event.getSendEmail() == null) {
            emailSent = emailService.sendEmail(event);
            persistenceService.saveEmailLog(event, emailSent, emailSent ? null : "delivery_failed");
        }

        // Handle in-app notification creation
        if (Boolean.TRUE.equals(event.getCreateInAppNotification())) {
            try {
                inAppCreated = persistenceService.createNotification(event) != null;
            } catch (Exception e) {
                log.error("Failed to create in-app notification: {}", e.getMessage());
            }
        }

        return emailSent || inAppCreated;
    }

    @Override
    public int getPriority() {
        return 1000; // Lowest priority (fallback)
    }
}
