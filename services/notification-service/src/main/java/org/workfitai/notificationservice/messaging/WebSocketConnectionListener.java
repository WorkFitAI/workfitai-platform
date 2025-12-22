package org.workfitai.notificationservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.workfitai.notificationservice.service.NotificationPersistenceService;
import org.workfitai.notificationservice.service.RealtimeNotificationService;

import java.security.Principal;

/**
 * Listener for WebSocket connection events.
 * Pushes initial data when user subscribes to channels.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketConnectionListener {

    private final NotificationPersistenceService notificationPersistenceService;
    private final RealtimeNotificationService realtimeNotificationService;

    /**
     * When user subscribes to unread-count queue, push initial count
     */
    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String destination = headers.getDestination();
        Principal user = headers.getUser();

        log.info("[WebSocket] Subscribe event: destination={}, user={}", destination,
                user != null ? user.getName() : "null");

        if (destination != null && destination.contains("/queue/unread-count") && user != null) {
            String userEmail = user.getName();
            log.info("[WebSocket] User subscribed to unread-count: {}", userEmail);

            // Push initial unread count
            try {
                long unreadCount = notificationPersistenceService.getUnreadCount(userEmail);
                realtimeNotificationService.pushUnreadCountUpdate(userEmail, unreadCount);
                log.info("[WebSocket] Pushed initial unread count to {}: {}", userEmail, unreadCount);
            } catch (Exception e) {
                log.error("[WebSocket] Failed to push initial unread count to {}: {}", userEmail, e.getMessage());
            }
        }
    }
}
