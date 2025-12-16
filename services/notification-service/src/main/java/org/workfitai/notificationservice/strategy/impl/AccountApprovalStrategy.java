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

        // Send email
        boolean emailSent = emailService.sendEmail(event);

        // Create in-app notification for user dashboard
        boolean inAppCreated = false;
        try {
            inAppCreated = persistenceService.createNotification(event) != null;
        } catch (Exception e) {
            log.error("Failed to create in-app notification: {}", e.getMessage());
        }

        persistenceService.saveEmailLog(event, emailSent, emailSent ? null : "delivery_failed");

        return emailSent || inAppCreated;
    }

    @Override
    public int getPriority() {
        return 20; // High priority
    }
}
