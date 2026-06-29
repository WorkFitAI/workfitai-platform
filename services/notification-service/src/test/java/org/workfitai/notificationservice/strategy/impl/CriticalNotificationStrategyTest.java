package org.workfitai.notificationservice.strategy.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.model.Notification;
import org.workfitai.notificationservice.service.EmailService;
import org.workfitai.notificationservice.service.NotificationPersistenceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CriticalNotificationStrategyTest {

    @Mock
    private EmailService emailService;
    @Mock
    private NotificationPersistenceService persistenceService;

    private CriticalNotificationStrategy strategy;

    private void init() {
        strategy = new CriticalNotificationStrategy(emailService, persistenceService);
    }

    @ParameterizedTest
    @ValueSource(strings = { "PASSWORD_CHANGED", "SECURITY_ALERT", "ACCOUNT_LOCKED",
            "SUSPICIOUS_LOGIN", "2FA_ENABLED", "2FA_DISABLED" })
    void canHandle_returnsTrue_forEachCriticalEventType(String eventType) {
        init();
        NotificationEvent event = NotificationEvent.builder().eventType(eventType).build();

        assertThat(strategy.canHandle(event)).isTrue();
    }

    @Test
    void canHandle_returnsFalse_forNonCriticalEventType() {
        init();
        NotificationEvent event = NotificationEvent.builder().eventType("GENERAL").build();

        assertThat(strategy.canHandle(event)).isFalse();
    }

    @Test
    void canHandle_returnsFalse_whenEventTypeNull() {
        init();
        NotificationEvent event = NotificationEvent.builder().eventType(null).build();

        assertThat(strategy.canHandle(event)).isFalse();
    }

    @Test
    void process_forcesBothChannels_evenWhenFlagsSayFalse() {
        init();
        NotificationEvent event = NotificationEvent.builder()
                .sendEmail(false).createInAppNotification(false).build();
        when(emailService.sendEmail(event)).thenReturn(true);
        when(persistenceService.createNotification(event)).thenReturn(Notification.builder().id("n1").build());

        boolean result = strategy.process(event);

        assertThat(result).isTrue();
    }

    @Test
    void process_savesFailureLog_whenEmailFails() {
        init();
        NotificationEvent event = NotificationEvent.builder().build();
        when(emailService.sendEmail(event)).thenReturn(false);
        when(persistenceService.createNotification(event)).thenReturn(Notification.builder().id("n1").build());

        strategy.process(event);

        org.mockito.Mockito.verify(persistenceService).saveEmailLog(event, false, "delivery_failed");
    }

    @Test
    void process_returnsTrue_evenWhenInAppCreationThrows_ifEmailSucceeded() {
        init();
        NotificationEvent event = NotificationEvent.builder().build();
        when(emailService.sendEmail(event)).thenReturn(true);
        when(persistenceService.createNotification(event)).thenThrow(new RuntimeException("db down"));

        boolean result = strategy.process(event);

        assertThat(result).isTrue();
    }

    @Test
    void process_returnsFalse_whenBothChannelsFail() {
        init();
        NotificationEvent event = NotificationEvent.builder().build();
        when(emailService.sendEmail(event)).thenReturn(false);
        when(persistenceService.createNotification(event)).thenReturn(null);

        boolean result = strategy.process(event);

        assertThat(result).isFalse();
    }

    @Test
    void getPriority_returnsHighestPriority() {
        init();
        assertThat(strategy.getPriority()).isEqualTo(1);
    }
}
