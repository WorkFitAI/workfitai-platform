package org.workfitai.authservice.dto.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when a user changes their password.
 * This event is consumed by user-service to sync passwordHash.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordChangeEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    private String eventType; // "PASSWORD_CHANGED"

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("passwordData")
    private PasswordData passwordData;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PasswordData {
        @JsonProperty("userId")
        private String userId;

        @JsonProperty("username")
        private String username;

        @JsonProperty("email")
        private String email;

        @JsonProperty("newPasswordHash")
        private String newPasswordHash;

        @JsonProperty("passwordChangedAt")
        private Instant passwordChangedAt;

        @JsonProperty("changeReason")
        private String changeReason; // "USER_CHANGE" or "PASSWORD_RESET"
    }
}
