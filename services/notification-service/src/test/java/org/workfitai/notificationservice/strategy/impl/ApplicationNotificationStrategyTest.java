package org.workfitai.notificationservice.strategy.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.model.Notification;
import org.workfitai.notificationservice.service.EmailService;
import org.workfitai.notificationservice.service.NotificationPersistenceService;
import org.workfitai.notificationservice.service.RateLimiterService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationNotificationStrategyTest {

    @Mock
    private EmailService emailService;
    @Mock
    private NotificationPersistenceService persistenceService;
    @Mock
    private RateLimiterService rateLimiterService;

    private ApplicationNotificationStrategy strategy;

    private void init() {
        strategy = new ApplicationNotificationStrategy(emailService, persistenceService, rateLimiterService);
    }

    @Test
    void canHandle_returnsTrue_forLowercasePrefix() {
        init();
        NotificationEvent event = NotificationEvent.builder().notificationType("application_submitted").build();

        assertThat(strategy.canHandle(event)).isTrue();
    }

    @Test
    void canHandle_returnsTrue_forUppercasePrefix() {
        init();
        NotificationEvent event = NotificationEvent.builder().notificationType("APPLICATION_RECEIVED").build();

        assertThat(strategy.canHandle(event)).isTrue();
    }

    @Test
    void canHandle_returnsFalse_forOtherType() {
        init();
        NotificationEvent event = NotificationEvent.builder().notificationType("job_posted").build();

        assertThat(strategy.canHandle(event)).isFalse();
    }

    @Test
    void canHandle_returnsFalse_whenNotificationTypeNull() {
        init();
        NotificationEvent event = NotificationEvent.builder().notificationType(null).build();

        assertThat(strategy.canHandle(event)).isFalse();
    }

    @Test
    void process_createsInAppOnly_andReturnsFalse_whenRateLimitExceeded() {
        init();
        NotificationEvent event = NotificationEvent.builder().recipientEmail("alice@test.com").build();
        when(rateLimiterService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(false);

        boolean result = strategy.process(event);

        assertThat(result).isFalse();
        verify(persistenceService).createNotification(event);
        verify(emailService, never()).sendEmail(any());
    }

    @Test
    void process_continuesExecution_whenRateLimitedAndInAppCreationThrows() {
        init();
        NotificationEvent event = NotificationEvent.builder().recipientEmail("alice@test.com").build();
        when(rateLimiterService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(false);
        when(persistenceService.createNotification(event)).thenThrow(new RuntimeException("db down"));

        boolean result = strategy.process(event);

        assertThat(result).isFalse();
    }

    @Test
    void process_sendsEmailAndLogsSuccess_whenUnderLimitAndFlagsSet() {
        init();
        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail("alice@test.com").sendEmail(true).createInAppNotification(false).build();
        when(rateLimiterService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(emailService.sendEmail(event)).thenReturn(true);

        boolean result = strategy.process(event);

        assertThat(result).isTrue();
        verify(persistenceService).saveEmailLog(event, true, null);
    }

    @Test
    void process_doesNotLogEmail_whenEmailNotSent() {
        init();
        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail("alice@test.com").sendEmail(true).build();
        when(rateLimiterService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(emailService.sendEmail(event)).thenReturn(false);

        strategy.process(event);

        verify(persistenceService, never()).saveEmailLog(any(), anyBoolean(), any());
    }

    @Test
    void process_createsInAppNotification_whenFlagTrue() {
        init();
        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail("alice@test.com").sendEmail(false).createInAppNotification(true).build();
        when(rateLimiterService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(persistenceService.createNotification(event)).thenReturn(Notification.builder().id("n1").build());

        boolean result = strategy.process(event);

        assertThat(result).isTrue();
    }

    @Test
    void process_returnsFalse_whenInAppNotificationCreationThrows() {
        init();
        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail("alice@test.com").sendEmail(false).createInAppNotification(true).build();
        when(rateLimiterService.allowRequest(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(persistenceService.createNotification(event)).thenThrow(new RuntimeException("db down"));

        boolean result = strategy.process(event);

        assertThat(result).isFalse();
    }

    @Test
    void getPriority_returnsMediumPriority() {
        init();
        assertThat(strategy.getPriority()).isEqualTo(50);
    }
}
