package org.workfitai.notificationservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.workfitai.notificationservice.dto.HrNotificationSettings;
import org.workfitai.notificationservice.dto.NotificationSettings;

/**
 * Fallback implementation for UserServiceClient.
 * Provides graceful degradation when user-service is unavailable.
 */
@Component
@Slf4j
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public NotificationSettings getNotificationSettings(String email) {
        log.warn("User service unavailable, returning default notification settings for {}", email);

        // Empty settings -> isEmailEnabledForType()/isPushEnabledForType() default to
        // enabled (fail-open) when email/push sub-objects are null.
        return NotificationSettings.builder().build();
    }

    @Override
    public HrNotificationSettings getHrNotificationSettings(String email) {
        log.warn("User service unavailable, returning default HR notification settings for {}", email);

        // Fail-open, matches getNotificationSettings()'s resilience characteristics.
        return HrNotificationSettings.builder()
                .notifyOnNewApplication(true)
                .notifyOnJobExpiry(true)
                .build();
    }

    @Override
    public java.util.Map<String, Object> getUserByUsername(String username) {
        log.warn("User service unavailable, cannot fetch user details for username: {}", username);
        return null;
    }
}
