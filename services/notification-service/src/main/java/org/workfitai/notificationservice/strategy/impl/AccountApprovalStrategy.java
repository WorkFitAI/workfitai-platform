package org.workfitai.notificationservice.strategy.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.service.EmailService;
import org.workfitai.notificationservice.service.NotificationPersistenceService;
import org.workfitai.notificationservice.strategy.NotificationStrategy;

/**
 * Strategy for account approval/activation notifications.
 * Sends both email + in-app for important account events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountApprovalStrategy implements NotificationStrategy {

    private final EmailService emailService;
    private final NotificationPersistenceService persistenceService;

    @Override
    public boolean canHandle(NotificationEvent event) {
        String template = event.getTemplateType();
        return template != null && (template.equals("APPROVAL_GRANTED") ||
                template.equals("ACCOUNT_ACTIVATED") ||
                template.equals("PENDING_APPROVAL") ||
                template.equals("ACCOUNT_REJECTED"));
    }

    @Override
    public boolean process(NotificationEvent event) {
        log.info("[APPROVAL] Processing approval notification: {} for {}",
                event.getTemplateType(), event.getRecipientEmail());

        boolean emailSent = false;
        boolean inAppCreated = false;

        // Check sendEmail flag (default true for backward compatibility)
        if (event.getSendEmail() == null || Boolean.TRUE.equals(event.getSendEmail())) {
            emailSent = emailService.sendEmail(event);
            persistenceService.saveEmailLog(event, emailSent, emailSent ? null : "delivery_failed");
        } else {
            log.debug("Skipping email for {} - sendEmail=false", event.getRecipientEmail());
        }

        // Check createInAppNotification flag
        if (Boolean.TRUE.equals(event.getCreateInAppNotification())) {
            try {
                inAppCreated = persistenceService.createNotification(event) != null;
            } catch (Exception e) {
                log.error("Failed to create in-app notification: {}", e.getMessage());
            }
        } else {
            log.debug("Skipping in-app notification for {} - createInAppNotification={}",
                    event.getRecipientEmail(), event.getCreateInAppNotification());
        }

        return emailSent || inAppCreated;
    }

    @Override
    public int getPriority() {
        return 20; // High priority
    }
}
