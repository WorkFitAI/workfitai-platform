package org.workfitai.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.model.Notification;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for pushing real-time notifications to WebSocket clients.
 * Sends notifications to specific users through their WebSocket connections.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Push notification to a specific user in real-time via WebSocket.
     * The notification will be delivered to all sessions of that user.
     * 
     * @param userEmail    User's email address
     * @param notification The notification object to send
     */
    public void pushToUser(String userEmail, Notification notification) {
        if (userEmail == null || notification == null) {
            log.warn("[WebSocket] Cannot push notification: userEmail={}, notification={}", userEmail, notification);
            return;
        }

        try {
            // Send to user-specific queue
            // Destination: /user/{userEmail}/queue/notifications
            messagingTemplate.convertAndSendToUser(
                    userEmail,
                    "/queue/notifications",
                    notification);

            log.info("[WebSocket] Pushed notification to user: email={}, notificationId={}, type={}",
                    userEmail, notification.getId(), notification.getType());

        } catch (Exception e) {
            log.error("[WebSocket] Failed to push notification to user {}: {}",
                    userEmail, e.getMessage(), e);
        }
    }

    /**
     * Push notification count update to user
     * 
     * @param userEmail   User's email address
     * @param unreadCount Current unread notification count
     */
    public void pushUnreadCountUpdate(String userEmail, long unreadCount) {
        if (userEmail == null) {
            return;
        }

        try {
            Map<String, Long> payload = new HashMap<>();
            payload.put("count", unreadCount);

            messagingTemplate.convertAndSendToUser(
                    userEmail,
                    "/queue/unread-count",
                    payload);

            log.debug("[WebSocket] Pushed unread count to user: email={}, count={}", userEmail, unreadCount);

        } catch (Exception e) {
            log.error("[WebSocket] Failed to push unread count to user {}: {}",
                    userEmail, e.getMessage(), e);
        }
    }

    /**
     * Push notification from NotificationEvent
     * Creates a simple payload from the event before sending
     * 
     * @param event The notification event
     */
    public void pushEventToUser(NotificationEvent event) {
        if (event == null || event.getRecipientEmail() == null) {
            log.warn("[WebSocket] Cannot push notification event: event is null or missing recipient");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", event.getNotificationType());
            payload.put("title", event.getSubject());
            payload.put("message", event.getContent());
            payload.put("actionUrl", event.getActionUrl());
            payload.put("timestamp", event.getTimestamp());
            payload.put("eventId", event.getEventId());

            messagingTemplate.convertAndSendToUser(
                    event.getRecipientEmail(),
                    "/queue/notifications",
                    payload);

            log.info("[WebSocket] Pushed event to user: email={}, eventType={}",
                    event.getRecipientEmail(), event.getEventType());

        } catch (Exception e) {
            log.error("[WebSocket] Failed to push event to user {}: {}",
                    event.getRecipientEmail(), e.getMessage(), e);
        }
    }

    /**
     * Broadcast a notification to all connected users (for system-wide
     * announcements)
     * Use sparingly as this sends to ALL users
     * 
     * @param notification The notification to broadcast
     */
    public void broadcastToAll(Notification notification) {
        try {
            messagingTemplate.convertAndSend("/topic/notifications", notification);
            log.info("[WebSocket] Broadcasted notification to all users: type={}", notification.getType());
        } catch (Exception e) {
            log.error("[WebSocket] Failed to broadcast notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Push a generic message to a user's queue
     * 
     * @param userEmail   User's email
     * @param destination Destination queue (without /user/{email} prefix)
     * @param payload     Message payload
     */
    public void sendToUser(String userEmail, String destination, Object payload) {
        if (userEmail == null || destination == null || payload == null) {
            log.warn("[WebSocket] Cannot send message: missing required parameters");
            return;
        }

        try {
            messagingTemplate.convertAndSendToUser(userEmail, destination, payload);
            log.debug("[WebSocket] Sent message to user: email={}, destination={}", userEmail, destination);
        } catch (Exception e) {
            log.error("[WebSocket] Failed to send message to user {}: {}", userEmail, e.getMessage(), e);
        }
    }
}
