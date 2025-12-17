package org.workfitai.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacySettingsResponse {
    private String profileVisibility;
    private Boolean showEmail;
    private Boolean showPhone;
    private Boolean showLocation;
    private Boolean allowCvDownload;
    private Boolean allowMessaging;
    private Boolean showActivityStatus;
    private Boolean showOnlineStatus;
    private Boolean searchIndexing;
}
