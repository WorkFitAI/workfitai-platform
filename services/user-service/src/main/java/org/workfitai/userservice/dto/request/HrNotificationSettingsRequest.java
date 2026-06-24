package org.workfitai.userservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrNotificationSettingsRequest {

    @NotNull(message = "Notify on new application setting is required")
    private Boolean notifyOnNewApplication;

    @NotNull(message = "Notify on job expiry setting is required")
    private Boolean notifyOnJobExpiry;
}
