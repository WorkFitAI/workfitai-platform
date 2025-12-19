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

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationPersistenceService persistenceService;

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
     * Extract email from JWT authentication
     */
    private String extractEmailFromAuth(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            // ✅ Get email from "email" claim (added in auth-service JwtService)
            String email = jwt.getClaimAsString("email");
            if (email != null) {
                return email;
            }
            // Fallback to subject for backward compatibility
            return jwt.getSubject();
        }
        return null;
    }
}
