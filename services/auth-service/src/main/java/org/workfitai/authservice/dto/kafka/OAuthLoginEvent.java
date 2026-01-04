package org.workfitai.authservice.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.authservice.enums.EventType;
import org.workfitai.authservice.enums.Provider;

import java.time.Instant;

/**
 * Kafka event published when user logs in via OAuth
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthLoginEvent {

    private String eventId; // UUID

    private EventType eventType; // OAUTH_LOGIN enum

    @Builder.Default
    private Instant timestamp = Instant.now();

    private String userId;

    private String username;

    private String email;

    private Provider provider; // GOOGLE | GITHUB enum

    private Boolean isNewUser; // True if auto-registered

    private LoginMetadata loginMetadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginMetadata {
        private String ipAddress;
        private String userAgent;
        private String deviceType; // "Desktop", "Mobile", "Tablet"
        private String browser;
        private String os;
        private String location; // Derived from IP (optional)
    }
}
