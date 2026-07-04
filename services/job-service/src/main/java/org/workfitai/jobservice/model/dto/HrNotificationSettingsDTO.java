package org.workfitai.jobservice.model.dto;

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
public class HrNotificationSettingsDTO {
    private Boolean notifyOnNewApplication;
    private Boolean notifyOnJobExpiry;

    public boolean isNotifyOnJobExpiryEnabled() {
        return notifyOnJobExpiry == null || notifyOnJobExpiry;
    }
}
