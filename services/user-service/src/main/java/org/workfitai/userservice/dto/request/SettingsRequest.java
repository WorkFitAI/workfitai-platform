package org.workfitai.userservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsRequest {

    @Valid
    private NotificationSettingsRequest notifications;

    @Valid
    private PrivacySettingsRequest privacy;

    /** HR / HR_MANAGER only — silently ignored for other roles. */
    @Valid
    private HrNotificationSettingsRequest hrNotifications;

    /** ADMIN only — silently ignored for other roles. */
    private List<@Valid FeatureToggleEntry> features;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureToggleEntry {
        @NotBlank(message = "Feature key is required")
        private String featureKey;
        @NotNull(message = "Enabled flag is required")
        private Boolean enabled;
    }
}
