package org.workfitai.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.workfitai.notificationservice.dto.NotificationSettings;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private UserPreferenceService userPreferenceService;

    private void init() {
        userPreferenceService = new UserPreferenceService(restTemplate);
        ReflectionTestUtils.setField(userPreferenceService, "userServiceUrl", "http://user-service:9081");
    }

    private NotificationEvent.NotificationEventBuilder baseEvent() {
        return NotificationEvent.builder().recipientEmail("alice@test.com");
    }

    private void stubSettings(NotificationSettings settings) {
        when(restTemplate.getForObject(anyString(), eq(NotificationSettings.class), eq("alice@test.com")))
                .thenReturn(settings);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PASSWORD_CHANGED", "SECURITY_ALERT", "2FA_ENABLED", "LOGIN_FAILED", "OTP_SENT",
            "APPROVAL_GRANTED", "ACCOUNT_ACTIVATED", "ACCOUNT_DEACTIVATED"
    })
    void shouldSendNotification_bypassesPreferenceCheck_forCriticalEventTypes(String eventType) {
        init();
        NotificationEvent event = baseEvent().eventType(eventType).build();

        boolean result = userPreferenceService.shouldSendNotification(event);

        assertThat(result).isTrue();
    }

    @Test
    void shouldSendNotification_returnsFalse_whenNoRecipientIdentifier() {
        init();
        NotificationEvent event = NotificationEvent.builder().eventType("GENERAL").build();

        boolean result = userPreferenceService.shouldSendNotification(event);

        assertThat(result).isFalse();
    }

    @Test
    void shouldSendNotification_defaultsToTrue_whenSettingsNotFound() {
        init();
        NotificationEvent event = baseEvent().eventType("GENERAL").build();
        stubSettings(null);

        boolean result = userPreferenceService.shouldSendNotification(event);

        assertThat(result).isTrue();
    }

    @Test
    void shouldSendNotification_returnsFalse_whenJobAlertsDisabledForJobCreated() {
        init();
        NotificationEvent event = baseEvent().eventType("GENERAL").templateType("job-created").build();
        stubSettings(NotificationSettings.builder()
                .email(NotificationSettings.EmailNotifications.builder().jobAlerts(false).build())
                .build());

        boolean result = userPreferenceService.shouldSendNotification(event);

        assertThat(result).isFalse();
    }

    @Test
    void shouldSendNotification_returnsTrue_whenJobAlertsEnabledForJobCreated() {
        init();
        NotificationEvent event = baseEvent().eventType("GENERAL").templateType("job-created").build();
        stubSettings(NotificationSettings.builder()
                .email(NotificationSettings.EmailNotifications.builder().jobAlerts(true).build())
                .build());

        boolean result = userPreferenceService.shouldSendNotification(event);

        assertThat(result).isTrue();
    }

    @Test
    void shouldSendNotification_returnsFalse_whenApplicationUpdatesDisabledForNewApplicationHr() {
        init();
        NotificationEvent event = baseEvent().eventType("GENERAL").templateType("new-application-hr").build();
        stubSettings(NotificationSettings.builder()
                .email(NotificationSettings.EmailNotifications.builder().applicationUpdates(false).build())
                .build());

        boolean result = userPreferenceService.shouldSendNotification(event);

        assertThat(result).isFalse();
    }

    @Test
    void shouldSendNotification_defaultsToTrue_whenRestTemplateThrows() {
        init();
        NotificationEvent event = baseEvent().eventType("GENERAL").build();
        when(restTemplate.getForObject(anyString(), eq(NotificationSettings.class), any(Object[].class)))
                .thenThrow(new RuntimeException("user-service down"));

        boolean result = userPreferenceService.shouldSendNotification(event);

        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "cv-upload-success", "cv-parsed", "application-confirmation",
            "new-application-hr", "password-changed", "password-reset", "password-set", "unknown-type" })
    void shouldSendNotification_returnsTrue_forEachTemplateType_whenNoExplicitToggleDisablesIt(String templateType) {
        init();
        NotificationEvent event = baseEvent().eventType("GENERAL").templateType(templateType).build();
        stubSettings(NotificationSettings.builder()
                .email(NotificationSettings.EmailNotifications.builder()
                        .jobAlerts(true).applicationUpdates(true).build())
                .build());

        boolean result = userPreferenceService.shouldSendNotification(event);

        assertThat(result).isTrue();
    }

    @Test
    void shouldSendNotification_returnsFalse_whenMarketingEmailsOptedOut() {
        init();
        NotificationEvent event = baseEvent().eventType("GENERAL").templateType("marketing-promo").build();
        stubSettings(NotificationSettings.builder()
                .email(NotificationSettings.EmailNotifications.builder().marketingEmails(false).build())
                .build());

        boolean result = userPreferenceService.shouldSendNotification(event);

        assertThat(result).isFalse();
    }

    @Test
    void shouldSendNotification_returnsFalse_whenNewsletterOptedOut() {
        init();
        NotificationEvent event = baseEvent().eventType("GENERAL").templateType("newsletter-digest").build();
        stubSettings(NotificationSettings.builder()
                .email(NotificationSettings.EmailNotifications.builder().newsletter(false).build())
                .build());

        boolean result = userPreferenceService.shouldSendNotification(event);

        assertThat(result).isFalse();
    }
}
