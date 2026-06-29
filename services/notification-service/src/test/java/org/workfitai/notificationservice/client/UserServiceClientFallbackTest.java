package org.workfitai.notificationservice.client;

import org.junit.jupiter.api.Test;
import org.workfitai.notificationservice.dto.HrNotificationSettings;
import org.workfitai.notificationservice.dto.NotificationSettings;

import static org.assertj.core.api.Assertions.assertThat;

class UserServiceClientFallbackTest {

    private final UserServiceClientFallback fallback = new UserServiceClientFallback();

    @Test
    void getNotificationSettings_returnsSettingsThatDefaultToEnabled() {
        NotificationSettings settings = fallback.getNotificationSettings("alice@test.com");

        assertThat(settings.isEmailEnabledForType("job-created")).isTrue();
        assertThat(settings.isPushEnabledForType("job-created")).isTrue();
    }

    @Test
    void getHrNotificationSettings_returnsSettingsThatDefaultToEnabled() {
        HrNotificationSettings settings = fallback.getHrNotificationSettings("hr@test.com");

        assertThat(settings.isNotifyOnNewApplicationEnabled()).isTrue();
    }

    @Test
    void getUserByUsername_returnsNull() {
        assertThat(fallback.getUserByUsername("alice")).isNull();
    }
}
