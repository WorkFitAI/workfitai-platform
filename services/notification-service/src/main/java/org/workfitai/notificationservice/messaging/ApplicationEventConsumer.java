package org.workfitai.notificationservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.workfitai.notificationservice.client.UserServiceClient;
import org.workfitai.notificationservice.dto.kafka.ApplicationCreatedEvent;
import org.workfitai.notificationservice.service.NotificationPersistenceService;
import org.workfitai.notificationservice.service.TemplateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;

import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer for application events.
 *
 * Handles:
 * - APPLICATION_CREATED: Send emails to candidate and HR when a new application
 * is submitted
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationEventConsumer {

    private final JavaMailSender mailSender;
    private final NotificationPersistenceService persistenceService;
    private final TemplateService templateService;
    private final UserServiceClient userServiceClient;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @KafkaListener(topics = "${app.kafka.topics.application-events:application-events}", groupId = "${spring.kafka.consumer.group-id:notification-service-group}")
    public void handleApplicationCreated(@Payload ApplicationCreatedEvent event) {
        if (event == null || event.getData() == null) {
            log.warn("Received null application created event");
            return;
        }

        ApplicationCreatedEvent.ApplicationData data = event.getData();
        log.info("[ApplicationCreated] applicationId={}, candidate={}, job={}, hr={}",
                data.getApplicationId(),
                data.getUsername(),
                data.getJobTitle(),
                data.getHrUsername());

        try {
            // Send confirmation email to candidate
            sendCandidateConfirmationEmail(data);

            // Send notification email to HR
            sendHrNotificationEmail(data);

            log.info("Application created notifications sent successfully: {}", data.getApplicationId());
        } catch (Exception e) {
            log.error("Failed to process application created event: {}", e.getMessage(), e);
        }
    }

    private void sendCandidateConfirmationEmail(ApplicationCreatedEvent.ApplicationData data) {
        try {
            // Fetch candidate email from auth-service
            String candidateEmail = fetchUserEmail(data.getUsername());
            if (candidateEmail == null) {
                log.warn("Cannot send candidate email: email not found for username={}", data.getUsername());
                return;
            }

            if (!StringUtils.hasText(mailUsername) || !StringUtils.hasText(mailPassword)) {
                log.warn("Skip candidate email: mail credentials are not configured");
                return;
            }

            // Prepare email variables
            Map<String, Object> variables = new HashMap<>();
            variables.put("candidateName",
                    data.getCandidateName() != null ? data.getCandidateName() : data.getUsername());
            variables.put("jobTitle", data.getJobTitle());
            variables.put("companyName", data.getCompanyName());
            variables.put("applicationId", data.getApplicationId());
            variables.put("appliedAt", data.getAppliedAt().toString());

            // Process email template
            String htmlContent = templateService.processTemplate("application-confirmation", variables);

            // Send email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(candidateEmail);
            helper.setSubject("Application Submitted: " + data.getJobTitle());
            helper.setText(htmlContent != null ? htmlContent : buildFallbackCandidateEmail(data), true);

            mailSender.send(message);
            log.info("Candidate confirmation email sent to {}", candidateEmail);

            // Save email log
            persistenceService.saveEmailLogForApplication(
                    data.getApplicationId(),
                    candidateEmail,
                    "Application Confirmation",
                    true,
                    null);

            // Create in-app notification for candidate
            createCandidateInAppNotification(data, candidateEmail);

        } catch (Exception e) {
            log.error("Failed to send candidate confirmation email: {}", e.getMessage(), e);
        }
    }

    private void sendHrNotificationEmail(ApplicationCreatedEvent.ApplicationData data) {
        if (data.getHrUsername() == null) {
            log.warn("Cannot send HR email: HR username is null for application={}", data.getApplicationId());
            return;
        }

        try {
            // Fetch HR email from auth-service
            String hrEmail = fetchUserEmail(data.getHrUsername());
            if (hrEmail == null) {
                log.warn("Cannot send HR email: email not found for username={}", data.getHrUsername());
                return;
            }

            if (!StringUtils.hasText(mailUsername) || !StringUtils.hasText(mailPassword)) {
                log.warn("Skip HR email: mail credentials are not configured");
                return;
            }

            // Prepare email variables
            Map<String, Object> variables = new HashMap<>();
            variables.put("hrName", data.getHrUsername());
            variables.put("candidateName",
                    data.getCandidateName() != null ? data.getCandidateName() : data.getUsername());
            variables.put("jobTitle", data.getJobTitle());
            variables.put("companyName", data.getCompanyName());
            variables.put("applicationId", data.getApplicationId());
            variables.put("appliedAt", data.getAppliedAt().toString());
            variables.put("cvFileUrl", data.getCvFileUrl());

            // Process email template
            String htmlContent = templateService.processTemplate("new-application-hr", variables);

            // Send email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(hrEmail);
            helper.setSubject("New Application: " + data.getJobTitle());
            helper.setText(htmlContent != null ? htmlContent : buildFallbackHrEmail(data), true);

            mailSender.send(message);
            log.info("HR notification email sent to {}", hrEmail);

            // Save email log
            persistenceService.saveEmailLogForApplication(
                    data.getApplicationId(),
                    hrEmail,
                    "New Application Notification",
                    true,
                    null);

            // Create in-app notification for HR
            createHrInAppNotification(data, hrEmail);

        } catch (Exception e) {
            log.error("Failed to send HR notification email: {}", e.getMessage(), e);
        }
    }

    private String fetchUserEmail(String username) {
        try {
            // Call user-service to get user email
            Map<String, Object> response = userServiceClient.getUserByUsername(username);
            if (response != null && response.get("data") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userData = (Map<String, Object>) response.get("data");
                return (String) userData.get("email");
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch email for username {}: {}", username, e.getMessage());
            return null;
        }
    }

    private String buildFallbackCandidateEmail(ApplicationCreatedEvent.ApplicationData data) {
        return String.format(
                """
                        <html>
                        <body>
                            <h2>Application Submitted Successfully</h2>
                            <p>Dear %s,</p>
                            <p>Your application for <strong>%s</strong> at <strong>%s</strong> has been submitted successfully.</p>
                            <p>Application ID: %s</p>
                            <p>We will review your application and get back to you soon.</p>
                            <p>Best regards,<br>WorkFitAI Team</p>
                        </body>
                        </html>
                        """,
                data.getCandidateName() != null ? data.getCandidateName() : data.getUsername(),
                data.getJobTitle(),
                data.getCompanyName(),
                data.getApplicationId());
    }

    private String buildFallbackHrEmail(ApplicationCreatedEvent.ApplicationData data) {
        return String.format("""
                <html>
                <body>
                    <h2>New Application Received</h2>
                    <p>Dear HR,</p>
                    <p>A new application has been received for <strong>%s</strong>.</p>
                    <p><strong>Candidate:</strong> %s</p>
                    <p><strong>Company:</strong> %s</p>
                    <p><strong>Application ID:</strong> %s</p>
                    <p><strong>CV:</strong> <a href="%s">View CV</a></p>
                    <p>Please review the application in your dashboard.</p>
                    <p>Best regards,<br>WorkFitAI System</p>
                </body>
                </html>
                """,
                data.getJobTitle(),
                data.getCandidateName() != null ? data.getCandidateName() : data.getUsername(),
                data.getCompanyName(),
                data.getApplicationId(),
                data.getCvFileUrl() != null ? data.getCvFileUrl() : "#");
    }

    private void createCandidateInAppNotification(ApplicationCreatedEvent.ApplicationData data, String candidateEmail) {
        try {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("applicationId", data.getApplicationId());
            notificationData.put("jobTitle", data.getJobTitle());
            notificationData.put("companyName", data.getCompanyName());
            notificationData.put("appliedAt", data.getAppliedAt().toString());

            NotificationEvent notifEvent = org.workfitai.notificationservice.dto.kafka.NotificationEvent
                    .builder()
                    .eventId(java.util.UUID.randomUUID().toString())
                    .eventType("APPLICATION_SUBMITTED")
                    .timestamp(java.time.Instant.now())
                    .recipientEmail(candidateEmail)
                    .recipientUserId(data.getUsername())
                    .subject("Application Submitted Successfully")
                    .content(String.format("Your application for %s at %s has been submitted.", data.getJobTitle(),
                            data.getCompanyName()))
                    .notificationType("application_submitted")
                    .sendEmail(false)
                    .createInAppNotification(true)
                    .referenceId(data.getApplicationId())
                    .referenceType("APPLICATION")
                    .metadata(notificationData)
                    .sourceService("application-service")
                    .build();

            persistenceService.createNotification(notifEvent);
            log.info("In-app notification created for candidate: {}", data.getUsername());
        } catch (Exception e) {
            log.error("Failed to create in-app notification for candidate: {}", e.getMessage(), e);
        }
    }

    private void createHrInAppNotification(ApplicationCreatedEvent.ApplicationData data, String hrEmail) {
        try {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("applicationId", data.getApplicationId());
            notificationData.put("candidateName",
                    data.getCandidateName() != null ? data.getCandidateName() : data.getUsername());
            notificationData.put("jobTitle", data.getJobTitle());
            notificationData.put("companyName", data.getCompanyName());
            notificationData.put("cvFileUrl", data.getCvFileUrl());
            notificationData.put("appliedAt", data.getAppliedAt().toString());

            NotificationEvent notifEvent = org.workfitai.notificationservice.dto.kafka.NotificationEvent
                    .builder()
                    .eventId(java.util.UUID.randomUUID().toString())
                    .eventType("NEW_APPLICATION")
                    .timestamp(java.time.Instant.now())
                    .recipientEmail(hrEmail)
                    .recipientUserId(data.getHrUsername())
                    .subject("New Application Received")
                    .content(String.format("%s applied for %s position.",
                            data.getCandidateName() != null ? data.getCandidateName() : data.getUsername(),
                            data.getJobTitle()))
                    .notificationType("application_received")
                    .sendEmail(false)
                    .createInAppNotification(true)
                    .referenceId(data.getApplicationId())
                    .referenceType("APPLICATION")
                    .metadata(notificationData)
                    .sourceService("application-service")
                    .build();

            persistenceService.createNotification(notifEvent);
            log.info("In-app notification created for HR: {}", data.getHrUsername());
        } catch (Exception e) {
            log.error("Failed to create in-app notification for HR: {}", e.getMessage(), e);
        }
    }
}
