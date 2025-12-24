package org.workfitai.notificationservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.workfitai.notificationservice.model.EmailLog;
import org.workfitai.notificationservice.model.Notification;
import org.workfitai.notificationservice.service.NotificationPersistenceService;
import org.workfitai.notificationservice.service.RealtimeNotificationService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationPersistenceService persistenceService;
    private final RealtimeNotificationService realtimeService;

    /**
     * Get notifications for the current authenticated user
     */
    @GetMapping
    public ResponseEntity<Page<Notification>> getNotifications(
            Authentication authentication,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        String userEmail = extractEmailFromAuth(authentication);
        if (userEmail == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(persistenceService.getNotificationsByEmail(userEmail, PageRequest.of(page, size)));
    }

    /**
     * Get unread notification count for current user
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        String userEmail = extractEmailFromAuth(authentication);
        if (userEmail == null) {
            return ResponseEntity.status(401).build();
        }
        long count = persistenceService.getUnreadCount(userEmail);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Mark a notification as read
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Notification> markAsRead(
            @PathVariable String id,
            Authentication authentication) {
        String userEmail = extractEmailFromAuth(authentication);
        if (userEmail == null) {
            return ResponseEntity.status(401).build();
        }

        Notification notification = persistenceService.markAsRead(id);
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }

        // Verify the notification belongs to the current user
        if (!userEmail.equals(notification.getUserEmail())) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(notification);
    }

    /**
     * Mark all notifications as read for current user
     */
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        String userEmail = extractEmailFromAuth(authentication);
        if (userEmail == null) {
            return ResponseEntity.status(401).build();
        }
        persistenceService.markAllAsRead(userEmail);
        return ResponseEntity.ok().build();
    }

    /**
     * Get email logs (admin endpoint)
     */
    @GetMapping("/email-logs")
    public ResponseEntity<List<EmailLog>> getEmailLogs(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(persistenceService.getLatestEmailLogs(limit));
    }

    /**
     * TEST ENDPOINT: Send a test notification to current user
     * POST /notification/test
     */
    // @PostMapping("/test")
    // public ResponseEntity<Map<String, Object>> sendTestNotification(
    //         Authentication authentication,
    //         @RequestBody(required = false) Map<String, String> request) {
    //     String userEmail = extractEmailFromAuth(authentication);
    //     if (userEmail == null) {
    //         return ResponseEntity.status(401).build();
    //     }

    //     // Create test notification using Builder pattern
    //     Notification notification = Notification.builder()
    //             .userId(username) // Add userId for WebSocket routing
    //             .userEmail(userEmail)
    //             .type(org.workfitai.notificationservice.model.NotificationType.GENERAL)
    //             .title(request != null && request.containsKey("title")
    //                     ? request.get("title")
    //                     : "🧪 Test Notification")
    //             .message(request != null && request.containsKey("message")
    //                     ? request.get("message")
    //                     : "This is a test notification sent at " + Instant.now())
    //             .read(false)
    //             .sourceService("notification-service")
    //             .build();

    //     // Save to database (this will also push to WebSocket)
    //     Notification saved = persistenceService.createNotification(
    //             org.workfitai.notificationservice.dto.kafka.NotificationEvent.builder()
    //                     .recipientEmail(userEmail)
    //                     .notificationType("general")
    //                     .subject(notification.getTitle())
    //                     .content(notification.getMessage())
    //                     .sourceService("notification-service")
    //                     .build());

    //     // Update unread count - use username for WebSocket
    //     long unreadCount = persistenceService.getUnreadCount(userEmail);
    //     realtimeService.pushUnreadCountUpdate(username, unreadCount);

    //     return ResponseEntity.ok(Map.of(
    //             "success", true,
    //             "notification", saved,
    //             "unreadCount", unreadCount));
    // }

    /**
     * Extract username from JWT authentication.
     * JWT subject contains username (not email).
     */
    private String extractEmailFromAuth(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            // JWT subject contains username
            return jwt.getSubject();
        }
        return null;
    }
}
