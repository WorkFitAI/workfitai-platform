package org.workfitai.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureToggleResponse {
    private String featureKey;
    private boolean enabled;
    private Instant updatedAt;
    private String updatedBy;
}
