package org.workfitai.authservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.workfitai.authservice.dto.kafka.NotificationEvent;
import org.workfitai.authservice.messaging.NotificationProducer;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test endpoint for triggering notifications from auth-service
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TestNotificationController {

    private final NotificationProducer notificationProducer;

    @PostMapping("/test-noti")
    public ResponseEntity<?> testNotification(
            @RequestParam(defaultValue = "PASSWORD_RESET") String type,
            Authentication authentication) {
        String username = authentication.getName();
        log.info("🧪 Test notification triggered by: {} (type: {})", username, type);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("username", username);
        metadata.put("testType", type);
        metadata.put("triggeredAt", Instant.now().toString());

        // For test purposes, use username as email (since username format is
        // email-like)
        String recipientEmail = username.contains("@") ? username : username + "@test.com";

        NotificationEvent event;

        switch (type.toUpperCase()) {
            case "OTP":
                metadata.put("otp", "123456");
                metadata.put("validUntil", Instant.now().plusSeconds(300).toString());
                event = NotificationEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("REGISTRATION")
                        .timestamp(Instant.now())
                        .recipientEmail(recipientEmail)
                        .recipientUserId(username)
                        .recipientRole("CANDIDATE")
                        .subject("Your OTP Code")
                        .templateType("otp-verification")
                        .sendEmail(true)
                        .createInAppNotification(true)
                        .sourceService("auth-service")
                        .metadata(metadata)
                        .build();
                break;

            case "PASSWORD_RESET":
            default:
                metadata.put("resetToken", UUID.randomUUID().toString());
                metadata.put("resetUrl", "http://localhost:3000/reset-password");
                event = NotificationEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType("PASSWORD_RESET")
                        .timestamp(Instant.now())
                        .recipientEmail(recipientEmail)
                        .recipientUserId(username)
                        .recipientRole("CANDIDATE")
                        .subject("Password Reset Request")
                        .templateType("password-reset")
                        .sendEmail(false)
                        .createInAppNotification(true)
                        .sourceService("auth-service")
                        .metadata(metadata)
                        .build();
                break;
        }

        notificationProducer.send(event);
        log.info("✅ Test notification sent: {} for user: {}", type, username);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Test notification sent",
                "type", type,
                "username", username));
    }
}
