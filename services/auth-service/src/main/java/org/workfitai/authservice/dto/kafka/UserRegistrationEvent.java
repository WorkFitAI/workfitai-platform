package org.workfitai.authservice.dto.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    private String eventType; // "USER_REGISTERED"

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("userData")
    private UserData userData;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserData {
        @JsonProperty("userId")
        private String userId;

        @JsonProperty("username")
        private String username;

        @JsonProperty("fullName")
        private String fullName;

        @JsonProperty("email")
        private String email;

        @JsonProperty("phoneNumber")
        private String phoneNumber;

        @JsonProperty("passwordHash")
        private String passwordHash;
    }
}