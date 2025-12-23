package org.workfitai.cvservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.workfitai.cvservice.dto.kafka.NotificationEvent;
import org.workfitai.cvservice.messaging.NotificationProducer;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test endpoint for triggering CV upload notifications
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TestNotificationController {

    private final NotificationProducer notificationProducer;

    @PostMapping("/test-noti")
    public ResponseEntity<?> testNotification(Authentication authentication) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("🧪 CV notification test triggered by: {}", username);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("cvId", UUID.randomUUID().toString());
        metadata.put("fileName", "test-resume.pdf");
        metadata.put("uploadedAt", Instant.now().toString());
        metadata.put("belongTo", username);
        metadata.put("type", "test-upload");
        metadata.put("fileUrl", "http://localhost:9000/test-cv.pdf");
        metadata.put("testTrigger", true);

        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("CV_UPLOADED")
                .timestamp(Instant.now())
                .recipientUserId(username)
                .recipientRole("CANDIDATE")
                .subject("CV Uploaded Successfully")
                .templateType("cv-upload-success")
                .sendEmail(true)
                .createInAppNotification(true)
                .referenceType("CV")
                .metadata(metadata)
                .build();

        notificationProducer.send(event);
        log.info("✅ CV upload test notification sent for user: {}", username);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "CV notification sent",
                "username", username,
                "fileName", metadata.get("fileName")));
    }
}
