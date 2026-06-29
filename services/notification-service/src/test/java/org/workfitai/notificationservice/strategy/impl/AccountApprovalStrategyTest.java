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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountApprovalStrategyTest {

    @Mock
    private EmailService emailService;
    @Mock
    private NotificationPersistenceService persistenceService;

    private AccountApprovalStrategy strategy;

    private void init() {
        strategy = new AccountApprovalStrategy(emailService, persistenceService);
    }

    @ParameterizedTest
    @ValueSource(strings = { "APPROVAL_GRANTED", "APPROVAL_REJECTED", "ACCOUNT_ACTIVATED",
            "PENDING_APPROVAL", "ACCOUNT_REJECTED" })
    void canHandle_returnsTrue_forEachSupportedTemplate(String templateType) {
        init();
        NotificationEvent event = NotificationEvent.builder().templateType(templateType).build();

        assertThat(strategy.canHandle(event)).isTrue();
    }

    @Test
    void canHandle_returnsFalse_forUnsupportedTemplate() {
        init();
        NotificationEvent event = NotificationEvent.builder().templateType("OTHER").build();

        assertThat(strategy.canHandle(event)).isFalse();
    }

    @Test
    void canHandle_returnsFalse_whenTemplateNull() {
        init();
        NotificationEvent event = NotificationEvent.builder().templateType(null).build();

        assertThat(strategy.canHandle(event)).isFalse();
    }

    @Test
    void process_sendsEmailAndLogsResult_whenSendEmailFlagTrue() {
        init();
        NotificationEvent event = NotificationEvent.builder().sendEmail(true).createInAppNotification(false).build();
        when(emailService.sendEmail(event)).thenReturn(true);

        boolean result = strategy.process(event);

        assertThat(result).isTrue();
        verify(persistenceService).saveEmailLog(event, true, null);
    }

    @Test
    void process_skipsEmail_whenSendEmailFlagFalse() {
        init();
        NotificationEvent event = NotificationEvent.builder().sendEmail(false).createInAppNotification(false).build();

        boolean result = strategy.process(event);

        assertThat(result).isFalse();
        verify(emailService, never()).sendEmail(any());
    }

    @Test
    void process_logsFailure_whenEmailSendFails() {
        init();
        NotificationEvent event = NotificationEvent.builder().sendEmail(true).build();
        when(emailService.sendEmail(event)).thenReturn(false);

        strategy.process(event);

        verify(persistenceService).saveEmailLog(event, false, "delivery_failed");
    }

    @Test
    void process_createsInAppNotification_whenFlagTrue() {
        init();
        NotificationEvent event = NotificationEvent.builder()
                .sendEmail(false).createInAppNotification(true).build();
        when(persistenceService.createNotification(event)).thenReturn(Notification.builder().id("n1").build());

        boolean result = strategy.process(event);

        assertThat(result).isTrue();
    }

    @Test
    void process_returnsFalse_whenInAppNotificationCreationThrows() {
        init();
        NotificationEvent event = NotificationEvent.builder()
                .sendEmail(false).createInAppNotification(true).build();
        when(persistenceService.createNotification(event)).thenThrow(new RuntimeException("db down"));

        boolean result = strategy.process(event);

        assertThat(result).isFalse();
    }

    @Test
    void getPriority_returnsHighPriority() {
        init();
        assertThat(strategy.getPriority()).isEqualTo(20);
    }
}
