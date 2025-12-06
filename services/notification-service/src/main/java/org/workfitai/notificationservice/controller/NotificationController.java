package org.workfitai.notificationservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
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
     * Get notifications for a user by email
     */
    @GetMapping
    public ResponseEntity<Page<Notification>> getNotifications(
            @RequestParam(name = "email") String email,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(persistenceService.getNotificationsByEmail(email, PageRequest.of(page, size)));
    }

    /**
     * Get unread notification count
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@RequestParam(name = "email") String email) {
        long count = persistenceService.getUnreadCount(email);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Mark a notification as read
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Notification> markAsRead(@PathVariable String id) {
        Notification notification = persistenceService.markAsRead(id);
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(notification);
    }

    /**
     * Mark all notifications as read for a user
     */
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@RequestParam(name = "email") String email) {
        persistenceService.markAllAsRead(email);
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
}
