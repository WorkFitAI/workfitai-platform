package org.workfitai.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.model.EmailLog;
import org.workfitai.notificationservice.model.Notification;
import org.workfitai.notificationservice.repository.EmailLogRepository;
import org.workfitai.notificationservice.repository.NotificationRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPersistenceServiceTest {

    @Mock
    private EmailLogRepository emailLogRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private RealtimeNotificationService realtimeService;

    private NotificationPersistenceService persistenceService;

    private void init() {
        persistenceService = new NotificationPersistenceService(emailLogRepository, notificationRepository, realtimeService);
    }

    @Test
    void saveEmailLog_savesEntityWithProvidedFields() {
        init();
        NotificationEvent event = NotificationEvent.builder()
                .eventId("evt-1").eventType("GENERAL").recipientEmail("alice@test.com").build();
        when(emailLogRepository.save(any(EmailLog.class))).thenAnswer(a -> a.getArgument(0));

        EmailLog result = persistenceService.saveEmailLog(event, true, null);

        assertThat(result.getEventId()).isEqualTo("evt-1");
        assertThat(result.isDelivered()).isTrue();
    }

    @Test
    void createNotification_savesAndPushesToWebSocket_byUserId() {
        init();
        NotificationEvent event = NotificationEvent.builder()
                .recipientUserId("alice").recipientEmail("alice@test.com")
                .subject("Hi").content("Hello").build();
        Notification saved = Notification.builder().id("n1").userId("alice").build();
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);
        when(notificationRepository.countByUserIdAndReadFalse("alice")).thenReturn(2L);

        Notification result = persistenceService.createNotification(event);

        assertThat(result.getId()).isEqualTo("n1");
        verify(realtimeService).pushToUser("alice", saved);
        verify(realtimeService).pushUnreadCountUpdate("alice", 2L);
    }

    @Test
    void createNotification_fallsBackToEmail_whenUserIdNull() {
        init();
        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail("alice@test.com").build();
        Notification saved = Notification.builder().id("n1").userEmail("alice@test.com").build();
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);
        when(notificationRepository.countByUserIdAndReadFalse("alice@test.com")).thenReturn(0L);

        persistenceService.createNotification(event);

        verify(realtimeService).pushToUser("alice@test.com", saved);
    }

    @Test
    void createNotification_continuesExecution_whenWebSocketPushFails() {
        init();
        NotificationEvent event = NotificationEvent.builder().recipientUserId("alice").build();
        Notification saved = Notification.builder().id("n1").userId("alice").build();
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);
        doThrow(new RuntimeException("ws down")).when(realtimeService).pushToUser(any(), any());

        Notification result = persistenceService.createNotification(event);

        assertThat(result.getId()).isEqualTo("n1");
    }

    @Test
    void createNotification_resolvesNotificationType_fromEventType() {
        init();
        NotificationEvent event = NotificationEvent.builder()
                .recipientUserId("alice").notificationType("job_posted").build();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(a -> a.getArgument(0));

        Notification result = persistenceService.createNotification(event);

        assertThat(result.getType()).isEqualTo(org.workfitai.notificationservice.model.NotificationType.JOB_POSTED);
    }

    @Test
    void getNotificationsByEmail_delegatesToRepository() {
        init();
        Page<Notification> page = new PageImpl<>(List.of());
        when(notificationRepository.findByUserEmailOrderByCreatedAtDesc("alice@test.com", PageRequest.of(0, 10)))
                .thenReturn(page);

        Page<Notification> result = persistenceService.getNotificationsByEmail("alice@test.com", PageRequest.of(0, 10));

        assertThat(result).isSameAs(page);
    }

    @Test
    void getNotificationsByUserId_delegatesToRepository() {
        init();
        Page<Notification> page = new PageImpl<>(List.of());
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc("alice", PageRequest.of(0, 10)))
                .thenReturn(page);

        Page<Notification> result = persistenceService.getNotificationsByUserId("alice", PageRequest.of(0, 10));

        assertThat(result).isSameAs(page);
    }

    @Test
    void getUnreadCount_delegatesToRepository() {
        init();
        when(notificationRepository.countByUserIdAndReadFalse("alice")).thenReturn(3L);

        long result = persistenceService.getUnreadCount("alice");

        assertThat(result).isEqualTo(3L);
    }

    @Test
    void getNotificationById_returnsNotification_whenFound() {
        init();
        Notification notification = Notification.builder().id("n1").build();
        when(notificationRepository.findById("n1")).thenReturn(Optional.of(notification));

        Notification result = persistenceService.getNotificationById("n1");

        assertThat(result).isSameAs(notification);
    }

    @Test
    void getNotificationById_returnsNull_whenNotFound() {
        init();
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

        Notification result = persistenceService.getNotificationById("missing");

        assertThat(result).isNull();
    }

    @Test
    void markAsRead_updatesAndPushesUnreadCount_whenFound() {
        init();
        Notification notification = Notification.builder().id("n1").userId("alice").read(false).build();
        when(notificationRepository.findById("n1")).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(a -> a.getArgument(0));
        when(notificationRepository.countByUserIdAndReadFalse("alice")).thenReturn(1L);

        Notification result = persistenceService.markAsRead("n1");

        assertThat(result.isRead()).isTrue();
        assertThat(result.getReadAt()).isNotNull();
        verify(realtimeService).pushUnreadCountUpdate("alice", 1L);
    }

    @Test
    void markAsRead_returnsNull_whenNotFound() {
        init();
        when(notificationRepository.findById("missing")).thenReturn(Optional.empty());

        Notification result = persistenceService.markAsRead("missing");

        assertThat(result).isNull();
        verify(realtimeService, never()).pushUnreadCountUpdate(anyString(), anyLong());
    }

    @Test
    void markAsRead_continuesExecution_whenWebSocketPushFails() {
        init();
        Notification notification = Notification.builder().id("n1").userId("alice").build();
        when(notificationRepository.findById("n1")).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(a -> a.getArgument(0));
        when(notificationRepository.countByUserIdAndReadFalse("alice")).thenThrow(new RuntimeException("db down"));

        Notification result = persistenceService.markAsRead("n1");

        assertThat(result).isNotNull();
    }

    @Test
    void markAllAsRead_marksEachAndPushesZeroUnreadCount() {
        init();
        Notification n1 = Notification.builder().id("n1").read(false).build();
        Notification n2 = Notification.builder().id("n2").read(false).build();
        when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc("alice")).thenReturn(List.of(n1, n2));

        persistenceService.markAllAsRead("alice");

        verify(notificationRepository).saveAll(anyList());
        verify(realtimeService).pushUnreadCountUpdate("alice", 0L);
        assertThat(n1.isRead()).isTrue();
        assertThat(n2.isRead()).isTrue();
    }

    @Test
    void markAllAsRead_continuesExecution_whenWebSocketPushFails() {
        init();
        when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc("alice")).thenReturn(List.of());
        doThrow(new RuntimeException("ws down")).when(realtimeService).pushUnreadCountUpdate(eq("alice"), eq(0L));

        persistenceService.markAllAsRead("alice");
        // no exception propagated
    }

    @Test
    void getLatestEmailLogs_clampsPageSizeToMaxLimit() {
        init();
        when(emailLogRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        persistenceService.getLatestEmailLogs(500);

        verify(emailLogRepository).findAll(argThatPageSize(200));
    }

    @Test
    void getLatestEmailLogs_clampsPageSizeToMinOne() {
        init();
        when(emailLogRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        persistenceService.getLatestEmailLogs(0);

        verify(emailLogRepository).findAll(argThatPageSize(1));
    }

    @Test
    void getFailedEmailLogs_delegatesToRepository() {
        init();
        when(emailLogRepository.findByDeliveredFalseOrderByTimestampDesc()).thenReturn(List.of());

        List<EmailLog> result = persistenceService.getFailedEmailLogs();

        assertThat(result).isEmpty();
    }

    @Test
    void saveEmailLogForApplication_savesEntityWithProvidedFields() {
        init();
        when(emailLogRepository.save(any(EmailLog.class))).thenAnswer(a -> a.getArgument(0));

        EmailLog result = persistenceService.saveEmailLogForApplication("app-1", "hr@test.com", "Subject", true, null);

        assertThat(result.getEventId()).isEqualTo("app-1");
        assertThat(result.isDelivered()).isTrue();
    }

    private static org.springframework.data.domain.Pageable argThatPageSize(int expectedSize) {
        return org.mockito.ArgumentMatchers.argThat(p -> p.getPageSize() == expectedSize);
    }
}
