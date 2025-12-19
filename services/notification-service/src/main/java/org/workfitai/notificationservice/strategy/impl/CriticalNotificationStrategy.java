package org.workfitai.notificationservice.strategy.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.enums.NotificationChannel;
import org.workfitai.notificationservice.enums.NotificationPriority;
import org.workfitai.notificationservice.service.EmailService;
import org.workfitai.notificationservice.service.NotificationPersistenceService;
import org.workfitai.notificationservice.strategy.NotificationStrategy;

/**
 * Strategy for CRITICAL notifications (security, password changes).
 * Always sends both email + in-app, bypasses rate limiting.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CriticalNotificationStrategy implements NotificationStrategy {

    private final EmailService emailService;
    private final NotificationPersistenceService persistenceService;

    @Override
    public boolean canHandle(NotificationEvent event) {
        return isCriticalEvent(event.getEventType());
    }

    @Override
    public boolean process(NotificationEvent event) {
        log.warn("[CRITICAL] Processing critical notification: {} for {}",
                event.getEventType(), event.getRecipientEmail());

        // Log warning if flags suggest not sending, but force anyway for security
        if (Boolean.FALSE.equals(event.getSendEmail())) {
            log.warn("[CRITICAL] sendEmail=false but forcing email for critical security notification");
        }
        if (Boolean.FALSE.equals(event.getCreateInAppNotification())) {
            log.warn("[CRITICAL] createInAppNotification=false but forcing in-app for critical notification");
        }

        // Force both channels for critical notifications (security events must be
        // delivered)
        boolean emailSent = emailService.sendEmail(event);
        boolean inAppCreated = false;

        try {
            inAppCreated = persistenceService.createNotification(event) != null;
        } catch (Exception e) {
            log.error("Failed to create critical in-app notification: {}", e.getMessage());
        }

        // Save email log
        persistenceService.saveEmailLog(event, emailSent, emailSent ? null : "delivery_failed");

        return emailSent || inAppCreated; // Success if at least one channel worked
    }

    @Override
    public int getPriority() {
        return 1; // Highest priority
    }

    private boolean isCriticalEvent(String eventType) {
        return eventType != null && (eventType.contains("PASSWORD_CHANGED") ||
                eventType.contains("SECURITY_ALERT") ||
                eventType.contains("ACCOUNT_LOCKED") ||
                eventType.contains("SUSPICIOUS_LOGIN") ||
                eventType.contains("2FA_ENABLED") ||
                eventType.contains("2FA_DISABLED"));
    }
}
