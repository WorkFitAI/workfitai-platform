package org.workfitai.userservice.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when a user links an OAuth provider to their account
 * Auth-service publishes this event to notify user-service to update linked
 * providers metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthAccountLinkedEvent {

    private String eventId;

    private String eventType; // OAUTH_ACCOUNT_LINKED

    private Instant timestamp;

    // User identification
    private String userId; // From auth-service (not user-service userId)
    private String username;
    private String email;

    // OAuth provider info
    private String provider; // GOOGLE | GITHUB
    private String providerEmail;

    // Request metadata
    private String ipAddress;
    private String userAgent;
}
