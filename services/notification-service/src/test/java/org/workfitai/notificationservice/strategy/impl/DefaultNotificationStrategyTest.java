package org.workfitai.notificationservice.strategy.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.notificationservice.client.UserServiceClient;
import org.workfitai.notificationservice.dto.NotificationSettings;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.model.Notification;
import org.workfitai.notificationservice.service.EmailService;
import org.workfitai.notificationservice.service.NotificationPersistenceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultNotificationStrategyTest {

    @Mock
    private EmailService emailService;
    @Mock
    private NotificationPersistenceService persistenceService;
    @Mock
    private UserServiceClient userServiceClient;

    private DefaultNotificationStrategy strategy;

    private void init() {
        strategy = new DefaultNotificationStrategy(emailService, persistenceService, userServiceClient);
    }

    @Test
    void canHandle_alwaysReturnsTrue() {
        init();
        assertThat(strategy.canHandle(NotificationEvent.builder().build())).isTrue();
    }

    @Test
    void process_sendsEmail_whenSendEmailNull() {
        // Per-type email gating happens inside EmailService (shared by every
        // strategy), not in this strategy - it just delegates and logs the result.
        init();
        NotificationEvent event = NotificationEvent.builder().recipientEmail("alice@test.com")
                .sendEmail(null).createInAppNotification(false).build();
        when(userServiceClient.getNotificationSettings("alice@test.com"))
                .thenReturn(NotificationSettings.builder().build());
        when(emailService.sendEmail(event)).thenReturn(true);

        boolean result = strategy.process(event);

        assertThat(result).isTrue();
        verify(persistenceService).saveEmailLog(event, true, null);
    }

    @Test
    void process_logsDeliveryFailed_whenEmailServiceDeclines() {
        init();
        NotificationEvent event = NotificationEvent.builder().recipientEmail("alice@test.com")
                .sendEmail(true).createInAppNotification(false).build();
        when(userServiceClient.getNotificationSettings("alice@test.com"))
                .thenReturn(NotificationSettings.builder().build());
        when(emailService.sendEmail(event)).thenReturn(false);

        boolean result = strategy.process(event);

        assertThat(result).isFalse();
        verify(persistenceService).saveEmailLog(event, false, "delivery_failed");
    }

    @Test
    void process_skipsEmail_whenSendEmailFlagFalse() {
        init();
        NotificationEvent event = NotificationEvent.builder().recipientEmail("alice@test.com")
                .sendEmail(false).build();
        when(userServiceClient.getNotificationSettings("alice@test.com"))
                .thenReturn(NotificationSettings.builder().build());

        boolean result = strategy.process(event);

        assertThat(result).isFalse();
        verify(emailService, never()).sendEmail(any());
    }

    @Test
    void process_createsInAppNotification_whenPushEnabledForType() {
        init();
        NotificationEvent event = NotificationEvent.builder().recipientEmail("alice@test.com")
                .templateType("job-created").sendEmail(false).createInAppNotification(true).build();
        when(userServiceClient.getNotificationSettings("alice@test.com"))
                .thenReturn(NotificationSettings.builder()
                        .push(NotificationSettings.PushNotifications.builder().jobAlerts(true).build())
                        .build());
        when(persistenceService.createNotification(event)).thenReturn(Notification.builder().id("n1").build());

        boolean result = strategy.process(event);

        assertThat(result).isTrue();
    }

    @Test
    void process_skipsInApp_whenPushDisabledForType() {
        init();
        NotificationEvent event = NotificationEvent.builder().recipientEmail("alice@test.com")
                .templateType("job-created").sendEmail(false).createInAppNotification(true).build();
        when(userServiceClient.getNotificationSettings("alice@test.com"))
                .thenReturn(NotificationSettings.builder()
                        .push(NotificationSettings.PushNotifications.builder().jobAlerts(false).build())
                        .build());

        boolean result = strategy.process(event);

        assertThat(result).isFalse();
        verify(persistenceService, never()).createNotification(any());
    }

    @Test
    void process_skipsInApp_whenCreateInAppFlagFalse() {
        init();
        NotificationEvent event = NotificationEvent.builder().recipientEmail("alice@test.com")
                .sendEmail(false).createInAppNotification(false).build();
        when(userServiceClient.getNotificationSettings("alice@test.com"))
                .thenReturn(NotificationSettings.builder().build());

        strategy.process(event);

        verify(persistenceService, never()).createNotification(any());
    }

    @Test
    void process_returnsFalse_whenInAppNotificationCreationThrows() {
        init();
        NotificationEvent event = NotificationEvent.builder().recipientEmail("alice@test.com")
                .templateType("job-created").sendEmail(false).createInAppNotification(true).build();
        when(userServiceClient.getNotificationSettings("alice@test.com"))
                .thenReturn(NotificationSettings.builder()
                        .push(NotificationSettings.PushNotifications.builder().jobAlerts(true).build())
                        .build());
        when(persistenceService.createNotification(event)).thenThrow(new RuntimeException("db down"));

        boolean result = strategy.process(event);

        assertThat(result).isFalse();
    }

    @Test
    void getPriority_returnsLowestPriority() {
        init();
        assertThat(strategy.getPriority()).isEqualTo(1000);
    }
}
