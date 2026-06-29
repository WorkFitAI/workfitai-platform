package org.workfitai.userservice.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Broadcast when an admin toggles a platform-wide AI feature
 * (job-recommendation, cv-referral). Consumed by recommendation-engine's
 * FeatureToggleConsumer to keep its local cache in sync.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureToggleChangedEvent {
    private String featureKey;
    private boolean enabled;
    private Instant updatedAt;
}
