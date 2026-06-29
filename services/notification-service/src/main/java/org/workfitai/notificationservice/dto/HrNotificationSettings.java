package org.workfitai.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors user-service's HrNotificationSettingsResponse shape.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrNotificationSettings {
    private Boolean notifyOnNewApplication;
    private Boolean notifyOnJobExpiry;

    public boolean isNotifyOnNewApplicationEnabled() {
        return notifyOnNewApplication == null || notifyOnNewApplication;
    }
}
