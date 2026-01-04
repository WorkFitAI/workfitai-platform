package org.workfitai.authservice.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.authservice.enums.EventType;
import org.workfitai.authservice.enums.Provider;

import java.time.Instant;

/**
 * Kafka event published when user unlinks an OAuth account
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthAccountUnlinkedEvent {

    private String eventId;

    private EventType eventType; // OAUTH_ACCOUNT_UNLINKED enum

    @Builder.Default
    private Instant timestamp = Instant.now();

    private String userId;

    private String username;

    private String email;

    private Provider provider; // GOOGLE | GITHUB enum

    private String ipAddress;

    private String userAgent;
}
