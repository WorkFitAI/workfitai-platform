package org.workfitai.authservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_sessions")
@CompoundIndex(name = "user_session_idx", def = "{'userId': 1, 'sessionId': 1}", unique = true)
public class UserSession {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed(unique = true)
    private String sessionId;

    private String refreshTokenHash;

    @Indexed
    private String deviceId;

    private String deviceName;

    private String ipAddress;

    private String userAgent;

    private Location location;

    @Indexed
    private LocalDateTime createdAt;

    private LocalDateTime lastActivityAt;

    @Indexed(expireAfterSeconds = 0)
    private LocalDateTime expiresAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String country;
        private String city;
        private String region;
        private Double latitude;
        private Double longitude;
    }
}
