package org.workfitai.notificationservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.service.NotificationPersistenceService;
import org.workfitai.notificationservice.service.TemplateService;

import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final JavaMailSender mailSender;
    private final NotificationPersistenceService persistenceService;
    private final TemplateService templateService;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @KafkaListener(topics = "${app.kafka.topics.notification:notification-events}", groupId = "${spring.kafka.consumer.group-id:notification-service-group}")
    public void handleNotification(@Payload NotificationEvent event) {
        if (event == null) {
            log.warn("Received null notification event");
            return;
        }

        log.info("[Notification] type={}, to={}, subject={}, source={}, sendEmail={}, createInApp={}",
                event.getEventType(),
                event.getRecipientEmail(),
                event.getSubject(),
                event.getSourceService(),
                event.getSendEmail(),
                event.getCreateInAppNotification());

        // Handle email sending
        boolean emailDelivered = false;
        String emailError = null;

        if (Boolean.TRUE.equals(event.getSendEmail()) || event.getSendEmail() == null) {
            emailDelivered = sendEmail(event);
            if (!emailDelivered) {
                emailError = "email_delivery_failed";
            }
            // Save email log
            persistenceService.saveEmailLog(event, emailDelivered, emailError);
        }

        // Handle in-app notification creation
        if (Boolean.TRUE.equals(event.getCreateInAppNotification())) {
            try {
                persistenceService.createNotification(event);
                log.info("In-app notification created for user: {}",
                        event.getRecipientUserId() != null ? event.getRecipientUserId() : event.getRecipientEmail());
            } catch (Exception e) {
                log.error("Failed to create in-app notification: {}", e.getMessage());
            }
        }
    }

    private boolean sendEmail(NotificationEvent event) {
        if (!StringUtils.hasText(event.getRecipientEmail())) {
            log.warn("Skip email: missing recipient for event {}", event.getEventId());
            return false;
        }
        if (!StringUtils.hasText(mailUsername) || !StringUtils.hasText(mailPassword)) {
            log.warn("Skip email: mail credentials are not configured");
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(event.getRecipientEmail());
            helper.setSubject(event.getSubject() == null ? "Notification" : event.getSubject());

            // Check if this is a template-based email
            String templateType = event.getTemplateType();
            if (StringUtils.hasText(templateType)) {
                String htmlContent = processEmailTemplate(templateType, event);
                if (htmlContent != null) {
                    helper.setText(htmlContent, true); // true for HTML
                } else {
                    // Fallback to plain text
                    helper.setText(event.getContent() == null ? "" : event.getContent(), false);
                }
            } else {
                // Plain text email
                helper.setText(event.getContent() == null ? "" : event.getContent(), false);
            }

            mailSender.send(message);
            log.info("Email sent to {} (template: {}, source: {})",
                    event.getRecipientEmail(), templateType, event.getSourceService());
            return true;
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", event.getRecipientEmail(), ex.getMessage(), ex);
            return false;
        }
    }

    private String processEmailTemplate(String templateType, NotificationEvent event) {
        try {
            String templateName = getTemplateName(templateType);
            if (templateName == null) {
                log.warn("No template mapping found for type: {}", templateType);
                return null;
            }

            Map<String, Object> variables = new HashMap<>();
            if (event.getMetadata() != null) {
                variables.putAll(event.getMetadata());
            }

            return templateService.processTemplate(templateName, variables);
        } catch (Exception e) {
            log.error("Error processing email template {}: {}", templateType, e.getMessage());
            return null;
        }
    }

    private String getTemplateName(String templateType) {
        return switch (templateType.toUpperCase()) {
            case "OTP_VERIFICATION" -> "otp-verification";
            case "ACCOUNT_ACTIVATED" -> "account-activated";
            case "PENDING_APPROVAL" -> "pending-approval";
            case "APPROVAL_GRANTED" -> "approval-granted";
            default -> null;
        };
    }
}
