package org.workfitai.authservice.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.authservice.enums.UserRole;
import org.workfitai.authservice.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * User change event from user-service for block/unblock operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserChangeEvent {

    private String eventId;
    private String eventType; // USER_BLOCKED, USER_UNBLOCKED, USER_CREATED, USER_UPDATED, USER_DELETED
    private Instant timestamp;
    private UUID userId;
    private Long version;
    private UserEventData data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserEventData {
        private UUID userId;
        private String username;
        private String fullName;
        private String email;
        private String phoneNumber;
        private UserRole userRole;
        private UserStatus userStatus;
        private boolean isBlocked;
        private boolean isDeleted;
        private Instant createdDate;
        private Instant lastModifiedDate;
        private Long version;
    }
}
