package org.workfitai.userservice.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event for user data changes.
 * Published when users are created, updated, deleted, blocked, or unblocked.
 * Consumed by Elasticsearch indexing service for search synchronization.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserChangeEvent {

    /**
     * Unique event identifier
     */
    private String eventId;

    /**
     * Type of change: USER_CREATED, USER_UPDATED, USER_DELETED, USER_BLOCKED,
     * USER_UNBLOCKED
     */
    private String eventType;

    /**
     * When the event was created
     */
    private Instant timestamp;

    /**
     * User ID affected by this change
     */
    private UUID userId;

    /**
     * Version for optimistic locking
     */
    private Long version;

    /**
     * User data snapshot at time of event
     */
    private UserEventData data;

    /**
     * Data payload for user change events
     */
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
        private EUserRole userRole;
        private EUserStatus userStatus;
        private boolean isBlocked;
        private boolean isDeleted;
        private Instant createdDate;
        private Instant lastModifiedDate;
        private Long version;
    }
}
