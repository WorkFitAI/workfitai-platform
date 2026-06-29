package org.workfitai.notificationservice.strategy.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.service.EmailService;
import org.workfitai.notificationservice.service.NotificationPersistenceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionalEmailStrategyTest {

    @Mock
    private EmailService emailService;
    @Mock
    private NotificationPersistenceService persistenceService;

    private TransactionalEmailStrategy strategy;

    private void init() {
        strategy = new TransactionalEmailStrategy(emailService, persistenceService);
    }

    @ParameterizedTest
    @ValueSource(strings = { "OTP_VERIFICATION", "PASSWORD_RESET", "FORGOT_PASSWORD" })
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
    void process_sendsEmail_whenSendEmailNull() {
        init();
        NotificationEvent event = NotificationEvent.builder().sendEmail(null).build();
        when(emailService.sendEmail(event)).thenReturn(true);

        boolean result = strategy.process(event);

        assertThat(result).isTrue();
        verify(persistenceService).saveEmailLog(event, true, null);
    }

    @Test
    void process_logsFailure_whenEmailSendFails() {
        init();
        NotificationEvent event = NotificationEvent.builder().sendEmail(true).build();
        when(emailService.sendEmail(event)).thenReturn(false);

        boolean result = strategy.process(event);

        assertThat(result).isFalse();
        verify(persistenceService).saveEmailLog(event, false, "delivery_failed");
    }

    @Test
    void process_skipsEmail_whenSendEmailFlagFalse() {
        init();
        NotificationEvent event = NotificationEvent.builder().sendEmail(false).build();

        boolean result = strategy.process(event);

        assertThat(result).isFalse();
        verify(emailService, never()).sendEmail(any());
    }

    @Test
    void getPriority_returnsHighPriority() {
        init();
        assertThat(strategy.getPriority()).isEqualTo(10);
    }
}
