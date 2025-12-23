package org.workfitai.jobservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.workfitai.jobservice.dto.kafka.NotificationEvent;
import org.workfitai.jobservice.messaging.NotificationProducer;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test endpoint for triggering job-related notifications
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TestNotificationController {

    private final NotificationProducer notificationProducer;

    @PostMapping("/test-noti")
    public ResponseEntity<?> testNotification(Authentication authentication) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("🧪 Job notification test triggered by: {}", username);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("jobTitle", "Senior Backend Developer (TEST)");
        metadata.put("jobId", UUID.randomUUID().toString());
        metadata.put("companyName", "WorkFitAI Tech");
        metadata.put("status", "PUBLISHED");
        metadata.put("location", "Ho Chi Minh City");
        metadata.put("employmentType", "FULL_TIME");
        metadata.put("testTrigger", true);

        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("JOB_CREATED")
                .timestamp(Instant.now())
                .recipientUserId(username)
                .recipientRole("HR")
                .subject("New Job Posted Successfully")
                .templateType("job-created")
                .sendEmail(true)
                .createInAppNotification(true)
                .referenceType("JOB")
                .metadata(metadata)
                .build();

        notificationProducer.send(event);
        log.info("✅ Job created test notification sent for user: {}", username);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Job notification sent",
                "username", username,
                "jobTitle", metadata.get("jobTitle")));
    }
}
