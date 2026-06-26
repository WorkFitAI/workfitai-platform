package org.workfitai.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsResponse {

    private String role;

    private NotificationSettingsResponse notifications;

    private PrivacySettingsResponse privacy;

    /** Populated only for HR and HR_MANAGER roles. */
    private HrNotificationSettingsResponse hrNotifications;

    /** Populated only for ADMIN role — platform-wide feature toggles. */
    private List<FeatureToggleResponse> features;
}
