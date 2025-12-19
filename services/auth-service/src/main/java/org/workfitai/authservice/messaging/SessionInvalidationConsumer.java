package org.workfitai.authservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.authservice.dto.kafka.SessionInvalidationEvent;
import org.workfitai.authservice.repository.UserSessionRepository;

/**
 * Kafka consumer for session invalidation events.
 * Listens for events from user-service when users are blocked or deleted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionInvalidationConsumer {

    private final UserSessionRepository sessionRepository;

    @KafkaListener(topics = "session-invalidation-events", groupId = "auth-service-session-invalidation", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handleSessionInvalidation(SessionInvalidationEvent event) {
        try {
            log.info("Received SESSION_INVALIDATION event for user: {} (reason: {})",
                    event.getUsername(), event.getReason());

            // Delete all sessions for the user
            int deletedCount = sessionRepository.deleteByUserId(event.getUserId().toString());

            log.info("Invalidated {} sessions for user {} (reason: {})",
                    deletedCount, event.getUsername(), event.getReason());

        } catch (Exception e) {
            log.error("Failed to handle session invalidation for user: {}", event.getUsername(), e);
            // Don't throw exception - let Kafka retry
        }
    }
}
