package org.workfitai.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.model.Notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RealtimeNotificationServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private RealtimeNotificationService realtimeNotificationService;

    private void init() {
        realtimeNotificationService = new RealtimeNotificationService(messagingTemplate);
    }

    @Test
    void pushToUser_sendsToUserQueue() {
        init();
        Notification notification = Notification.builder().id("n1").build();

        realtimeNotificationService.pushToUser("alice", notification);

        verify(messagingTemplate).convertAndSendToUser("alice", "/queue/notifications", notification);
    }

    @Test
    void pushToUser_doesNothing_whenUsernameNull() {
        init();
        Notification notification = Notification.builder().id("n1").build();

        realtimeNotificationService.pushToUser(null, notification);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void pushToUser_doesNothing_whenNotificationNull() {
        init();

        realtimeNotificationService.pushToUser("alice", null);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void pushToUser_swallowsException() {
        init();
        Notification notification = Notification.builder().id("n1").build();
        doThrow(new RuntimeException("ws down")).when(messagingTemplate)
                .convertAndSendToUser(eq("alice"), eq("/queue/notifications"), eq(notification));

        realtimeNotificationService.pushToUser("alice", notification);
        // no exception propagated
    }

    @Test
    void pushUnreadCountUpdate_sendsCountPayload() {
        init();

        realtimeNotificationService.pushUnreadCountUpdate("alice", 5L);

        verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/unread-count"), any());
    }

    @Test
    void pushUnreadCountUpdate_doesNothing_whenUsernameNull() {
        init();

        realtimeNotificationService.pushUnreadCountUpdate(null, 5L);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void pushUnreadCountUpdate_swallowsException() {
        init();
        doThrow(new RuntimeException("ws down")).when(messagingTemplate)
                .convertAndSendToUser(eq("alice"), eq("/queue/unread-count"), any());

        realtimeNotificationService.pushUnreadCountUpdate("alice", 5L);
        // no exception propagated
    }

    @Test
    void pushEventToUser_sendsPayload() {
        init();
        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail("alice@test.com").subject("Hi").content("Hello").build();

        realtimeNotificationService.pushEventToUser(event);

        verify(messagingTemplate).convertAndSendToUser(eq("alice@test.com"), eq("/queue/notifications"), any());
    }

    @Test
    void pushEventToUser_doesNothing_whenEventNull() {
        init();

        realtimeNotificationService.pushEventToUser(null);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void pushEventToUser_doesNothing_whenRecipientEmailNull() {
        init();
        NotificationEvent event = NotificationEvent.builder().recipientEmail(null).build();

        realtimeNotificationService.pushEventToUser(event);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void pushEventToUser_swallowsException() {
        init();
        NotificationEvent event = NotificationEvent.builder().recipientEmail("alice@test.com").build();
        doThrow(new RuntimeException("ws down")).when(messagingTemplate)
                .convertAndSendToUser(eq("alice@test.com"), eq("/queue/notifications"), any());

        realtimeNotificationService.pushEventToUser(event);
        // no exception propagated
    }

    @Test
    void broadcastToAll_sendsToTopic() {
        init();
        Notification notification = Notification.builder().id("n1").build();

        realtimeNotificationService.broadcastToAll(notification);

        verify(messagingTemplate).convertAndSend("/topic/notifications", notification);
    }

    @Test
    void broadcastToAll_swallowsException() {
        init();
        Notification notification = Notification.builder().id("n1").build();
        doThrow(new RuntimeException("ws down")).when(messagingTemplate)
                .convertAndSend(eq("/topic/notifications"), eq(notification));

        realtimeNotificationService.broadcastToAll(notification);
        // no exception propagated
    }

    @Test
    void sendToUser_sendsPayload() {
        init();

        realtimeNotificationService.sendToUser("alice@test.com", "/queue/custom", "payload");

        verify(messagingTemplate).convertAndSendToUser("alice@test.com", "/queue/custom", "payload");
    }

    @Test
    void sendToUser_doesNothing_whenAnyParamNull() {
        init();

        realtimeNotificationService.sendToUser(null, "/queue/custom", "payload");
        realtimeNotificationService.sendToUser("alice@test.com", null, "payload");
        realtimeNotificationService.sendToUser("alice@test.com", "/queue/custom", null);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void sendToUser_swallowsException() {
        init();
        doThrow(new RuntimeException("ws down")).when(messagingTemplate)
                .convertAndSendToUser(eq("alice@test.com"), eq("/queue/custom"), eq("payload"));

        realtimeNotificationService.sendToUser("alice@test.com", "/queue/custom", "payload");
        // no exception propagated
    }
}
