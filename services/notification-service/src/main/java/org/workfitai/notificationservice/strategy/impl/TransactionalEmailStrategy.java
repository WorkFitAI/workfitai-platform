package org.workfitai.notificationservice.strategy.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.service.EmailService;
import org.workfitai.notificationservice.service.NotificationPersistenceService;
import org.workfitai.notificationservice.strategy.NotificationStrategy;

/**
 * Strategy for transactional emails (OTP, password reset).
 * Email-only, no in-app notification needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionalEmailStrategy implements NotificationStrategy {

    private final EmailService emailService;
    private final NotificationPersistenceService persistenceService;

    @Override
    public boolean canHandle(NotificationEvent event) {
        String template = event.getTemplateType();
        return template != null && (template.equals("OTP_VERIFICATION") ||
                template.equals("PASSWORD_RESET") ||
                template.equals("FORGOT_PASSWORD"));
    }

    @Override
    public boolean process(NotificationEvent event) {
        log.info("[TRANSACTIONAL] Processing transactional email: {} for {}",
                event.getTemplateType(), event.getRecipientEmail());

        boolean emailSent = emailService.sendEmail(event);
        persistenceService.saveEmailLog(event, emailSent, emailSent ? null : "delivery_failed");

        return emailSent;
    }

    @Override
    public int getPriority() {
        return 10; // High priority
    }
}
