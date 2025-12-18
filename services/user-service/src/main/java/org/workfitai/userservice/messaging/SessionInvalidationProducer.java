package org.workfitai.userservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.workfitai.userservice.dto.kafka.SessionInvalidationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for publishing session invalidation events to Kafka.
 * These events are consumed by auth-service to invalidate user sessions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionInvalidationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "session-invalidation-events";

    /**
     * Publish session invalidation event when user is blocked
     */
    public void publishSessionInvalidation(UUID userId, String username, String reason) {
        try {
            SessionInvalidationEvent event = SessionInvalidationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("SESSION_INVALIDATION")
                    .timestamp(Instant.now())
                    .userId(userId)
                    .username(username)
                    .reason(reason)
                    .build();

            kafkaTemplate.send(TOPIC, userId.toString(), event);
            log.info("Published SESSION_INVALIDATION event for user: {} (reason: {})", userId, reason);
        } catch (Exception e) {
            log.error("Failed to publish SESSION_INVALIDATION event for user: {}", userId, e);
            // Don't throw exception - event publishing is async
        }
    }
}
