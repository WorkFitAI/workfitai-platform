package org.workfitai.userservice.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Event for session invalidation when user is blocked or deleted
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInvalidationEvent {

    private String eventId;
    private String eventType; // SESSION_INVALIDATION
    private Instant timestamp;
    private UUID userId;
    private String username;
    private String reason; // BLOCKED, DELETED
}
