package org.workfitai.notificationservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.workfitai.notificationservice.service.NotificationPersistenceService;
import org.workfitai.notificationservice.service.RealtimeNotificationService;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketConnectionListenerTest {

    @Mock
    private NotificationPersistenceService notificationPersistenceService;
    @Mock
    private RealtimeNotificationService realtimeNotificationService;

    private WebSocketConnectionListener listener;

    private void init() {
        listener = new WebSocketConnectionListener(notificationPersistenceService, realtimeNotificationService);
    }

    private Principal principal(String name) {
        return () -> name;
    }

    private SessionSubscribeEvent subscribeEvent(String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        Message<byte[]> message = org.springframework.messaging.support.MessageBuilder
                .withPayload(new byte[0]).setHeaders(accessor).build();
        return new SessionSubscribeEvent(this, message);
    }

    @Test
    void handleSubscribe_pushesInitialUnreadCount_whenSubscribingToUnreadCountQueue() {
        init();
        when(notificationPersistenceService.getUnreadCount("alice")).thenReturn(3L);

        listener.handleSubscribe(subscribeEvent("/user/queue/unread-count", principal("alice")));

        verify(realtimeNotificationService).pushUnreadCountUpdate("alice", 3L);
    }

    @Test
    void handleSubscribe_doesNothing_whenDestinationNotUnreadCount() {
        init();

        listener.handleSubscribe(subscribeEvent("/user/queue/notifications", principal("alice")));

        verify(realtimeNotificationService, never()).pushUnreadCountUpdate(org.mockito.ArgumentMatchers.anyString(), anyLong());
    }

    @Test
    void handleSubscribe_doesNothing_whenUserNull() {
        init();

        listener.handleSubscribe(subscribeEvent("/user/queue/unread-count", null));

        verify(realtimeNotificationService, never()).pushUnreadCountUpdate(org.mockito.ArgumentMatchers.anyString(), anyLong());
    }

    @Test
    void handleSubscribe_doesNothing_whenDestinationNull() {
        init();

        listener.handleSubscribe(subscribeEvent(null, principal("alice")));

        verify(realtimeNotificationService, never()).pushUnreadCountUpdate(org.mockito.ArgumentMatchers.anyString(), anyLong());
    }

    @Test
    void handleSubscribe_swallowsException_whenPersistenceServiceThrows() {
        init();
        when(notificationPersistenceService.getUnreadCount("alice")).thenThrow(new RuntimeException("db down"));

        listener.handleSubscribe(subscribeEvent("/user/queue/unread-count", principal("alice")));
        // no exception propagated
    }
}
