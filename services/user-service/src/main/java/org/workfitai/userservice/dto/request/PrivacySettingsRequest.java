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
public class PrivacySettingsRequest {

    @NotNull(message = "Profile visibility is required")
    private ProfileVisibility profileVisibility;

    @NotNull(message = "Show email setting is required")
    private Boolean showEmail;

    @NotNull(message = "Show phone setting is required")
    private Boolean showPhone;

    @NotNull(message = "Show location setting is required")
    private Boolean showLocation;

    @NotNull(message = "Allow messaging setting is required")
    private Boolean allowMessaging;

    @NotNull(message = "Show activity status setting is required")
    private Boolean showActivityStatus;

    @NotNull(message = "Show online status setting is required")
    private Boolean showOnlineStatus;

    @NotNull(message = "Search indexing setting is required")
    private Boolean searchIndexing;

    @NotNull(message = "AI job recommendation setting is required")
    private Boolean aiJobRecommendationEnabled;

    public enum ProfileVisibility {
        PUBLIC,
        RECRUITERS_ONLY,
        PRIVATE
    }
}
