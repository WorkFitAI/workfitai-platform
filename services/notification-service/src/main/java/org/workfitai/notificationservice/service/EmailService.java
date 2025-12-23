package org.workfitai.notificationservice.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;

/**
 * Extracted email sending logic for reuse across strategies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateService templateService;
    private final UserPreferenceService userPreferenceService;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.from:noreply@workfitai.com}")
    private String mailFrom;

    @Value("${app.mail.from-name:WorkFitAI}")
    private String mailFromName;

    public boolean sendEmail(NotificationEvent event) {
        if (!StringUtils.hasText(event.getRecipientEmail())) {
            log.warn("Skip email: missing recipient for event {}", event.getEventId());
            return false;
        }

        if (!StringUtils.hasText(mailUsername) || !StringUtils.hasText(mailPassword)) {
            log.warn("Skip email: mail credentials are not configured");
            return false;
        }

        // Check user notification preferences and privacy settings
        if (!userPreferenceService.shouldSendNotification(event)) {
            log.info("Skip email: user {} has disabled this notification type: {}",
                    event.getRecipientEmail(), event.getTemplateType());
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Gmail requires "From" to match authenticated user, use personal name instead
            helper.setFrom(mailUsername, mailFromName);
            helper.setTo(event.getRecipientEmail());
            helper.setSubject(event.getSubject() == null ? "Notification" : event.getSubject());

            // Check if this is a template-based email
            String templateType = event.getTemplateType();
            if (StringUtils.hasText(templateType)) {
                // Convert template type to kebab-case filename
                String templateName = convertToTemplateName(templateType);
                String htmlContent = templateService.processTemplate(templateName, event.getMetadata());
                if (htmlContent != null) {
                    helper.setText(htmlContent, true); // true for HTML
                } else {
                    // Fallback to plain text
                    helper.setText(event.getContent() == null ? "" : event.getContent(), false);
                }
            } else {
                // Use content directly
                helper.setText(event.getContent() == null ? "" : event.getContent(), false);
            }

            mailSender.send(message);
            log.info("Email sent successfully to: {} using template: {}",
                    event.getRecipientEmail(), templateType);
            return true;

        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", event.getRecipientEmail(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Convert template type to template filename.
     * Handles both UPPERCASE_SNAKE_CASE and kebab-case formats.
     * Examples:
     * - "OTP_VERIFICATION" -> "otp-verification"
     * - "otp-verification" -> "otp-verification"
     * - "password-reset" -> "password-reset"
     */
    private String convertToTemplateName(String templateType) {
        if (templateType == null) {
            return null;
        }

        // If already in kebab-case (contains hyphen), return as-is
        if (templateType.contains("-")) {
            return templateType.toLowerCase();
        }

        // Convert UPPERCASE_SNAKE_CASE to kebab-case
        return templateType.toLowerCase().replace("_", "-");
    }
}
