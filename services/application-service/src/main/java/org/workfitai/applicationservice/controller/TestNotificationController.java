package org.workfitai.applicationservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.workfitai.applicationservice.dto.kafka.NotificationEvent;
import org.workfitai.applicationservice.messaging.ApplicationEventProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test endpoint for triggering application notifications
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TestNotificationController {

    private final ApplicationEventProducer eventProducer;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.notification-events:application-notification-events}")
    private String notificationEventsTopic;

    @PostMapping("/test-noti")
    public ResponseEntity<?> testNotification(
            @RequestParam(defaultValue = "CANDIDATE") String type,
            Authentication authentication) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("🧪 Application notification test triggered by: {} (type: {})", username, type);

        // For test purposes, use username as email
        String recipientEmail = username.contains("@") ? username : username + "@test.com";

        String applicationId = UUID.randomUUID().toString();
        String jobTitle = "Senior Java Developer (TEST)";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("applicationId", applicationId);
        metadata.put("jobTitle", jobTitle);
        metadata.put("companyName", "WorkFitAI Tech");
        metadata.put("appliedAt", Instant.now().toString());
        metadata.put("status", "APPLIED");
        metadata.put("testTrigger", true);

        NotificationEvent event;

        if ("HR".equalsIgnoreCase(type)) {
            // Test HR notification
            metadata.put("candidateName", username);
            metadata.put("hrName", username);

            event = NotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("NEW_APPLICATION")
                    .timestamp(Instant.now())
                    .recipientEmail(recipientEmail)
                    .recipientUserId(username)
                    .recipientRole("HR")
                    .subject("New Application: " + jobTitle)
                    .templateType("NEW_APPLICATION_HR")
                    .sendEmail(false)
                    .createInAppNotification(true)
                    .referenceId(applicationId)
                    .referenceType("APPLICATION")
                    .sourceService("application-service")
                    .metadata(metadata)
                    .build();
        } else {
            // Test CANDIDATE notification
            metadata.put("candidateName", username);

            event = NotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("APPLICATION_SUBMITTED")
                    .timestamp(Instant.now())
                    .recipientEmail(recipientEmail)
                    .recipientUserId(username)
                    .recipientRole("CANDIDATE")
                    .subject("Application Submitted: " + jobTitle)
                    .templateType("APPLICATION_CONFIRMATION")
                    .sendEmail(false)
                    .createInAppNotification(true)
                    .referenceId(applicationId)
                    .referenceType("APPLICATION")
                    .sourceService("application-service")
                    .metadata(metadata)
                    .build();
        }

        // Use Kafka template to send notification event directly
        try {
            kafkaTemplate.send(notificationEventsTopic, username, event);
            log.info("✅ Application test notification sent: {} for user: {}", type, username);
        } catch (Exception e) {
            log.error("Failed to send test notification: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to send notification: " + e.getMessage()));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Application notification sent",
                "type", type,
                "username", username,
                "applicationId", applicationId));
    }
}
