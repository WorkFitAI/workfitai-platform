package org.workfitai.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrNotificationSettingsResponse {
    private Boolean notifyOnNewApplication;
    private Boolean notifyOnJobExpiry;
}
