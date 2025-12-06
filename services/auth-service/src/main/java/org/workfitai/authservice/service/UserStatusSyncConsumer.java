package org.workfitai.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.workfitai.authservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Kafka consumer for user status update events from user-service.
 * When user-service approves a user, it publishes an event which this consumer
 * handles to sync the status back to auth-service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserStatusSyncConsumer {

    private final UserRepository userRepository;

    @KafkaListener(topics = "${app.kafka.topics.user-registration:user-registration}", groupId = "auth-service-status-sync", containerFactory = "kafkaListenerContainerFactory")
    public void handleUserStatusUpdate(@Payload UserRegistrationEvent event) {
        log.info(">>> Received Kafka message on user-registration topic");

        if (event == null || event.getUserData() == null) {
            log.warn("Received null or invalid user status event");
            return;
        }

        String eventType = event.getEventType();
        log.info(">>> Event type: {}, Event ID: {}", eventType, event.getEventId());

        // Only process approval events from user-service
        if (!"HR_MANAGER_APPROVED".equals(eventType) && !"HR_APPROVED".equals(eventType)) {
            log.debug("Ignoring event type: {} (not an approval event)", eventType);
            return;
        }

        var userData = event.getUserData();
        String email = userData.getEmail();
        String newStatus = userData.getStatus();

        log.info("Processing {} event for user: {}, new status: {}", eventType, email, newStatus);

        try {
            Optional<User> userOpt = userRepository.findByEmail(email);

            if (userOpt.isEmpty()) {
                log.warn("User not found in auth-service: {}", email);
                return;
            }

            User user = userOpt.get();
            UserStatus targetStatus = UserStatus.fromString(newStatus);

            if (user.getStatus() == targetStatus) {
                log.info("User {} already has status {}, skipping update", email, targetStatus);
                return;
            }

            user.setStatus(targetStatus);
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);

            log.info("Successfully synced status for user {} to {}", email, targetStatus);

        } catch (Exception ex) {
            log.error("Error syncing user status for {}: {}", email, ex.getMessage(), ex);
        }
    }
}
