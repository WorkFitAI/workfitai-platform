package org.workfitai.authservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.workfitai.authservice.dto.kafka.UserChangeEvent;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Kafka consumer for user block/unblock events from user-service.
 * Syncs blocked status from user-service to auth-service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserBlockSyncConsumer {

    private final UserRepository userRepository;

    @KafkaListener(topics = "user-change-events", groupId = "auth-service-block-sync", containerFactory = "userChangeEventListenerContainerFactory")
    public void handleUserChangeEvent(@Payload UserChangeEvent event) {
        if (event == null || event.getData() == null) {
            log.warn("Received null or invalid user change event");
            return;
        }

        String eventType = event.getEventType();
        log.info("Received user change event: type={}, userId={}", eventType, event.getData().getUserId());

        // Only process BLOCKED and UNBLOCKED events
        if (!"USER_BLOCKED".equals(eventType) && !"USER_UNBLOCKED".equals(eventType)) {
            log.debug("Ignoring event type: {} (not a block/unblock event)", eventType);
            return;
        }

        var userData = event.getData();
        String username = userData.getUsername();
        boolean shouldBeBlocked = "USER_BLOCKED".equals(eventType);

        try {
            Optional<User> userOpt = userRepository.findByUsername(username);

            if (userOpt.isEmpty()) {
                log.warn("User not found in auth-service: {}", username);
                return;
            }

            User user = userOpt.get();

            if (Boolean.TRUE.equals(user.getIsBlocked()) == shouldBeBlocked) {
                log.info("User {} already has blocked={}, skipping update", username, shouldBeBlocked);
                return;
            }

            user.setIsBlocked(shouldBeBlocked);
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);

            log.info("Successfully synced blocked status for user {} to {}", username, shouldBeBlocked);

        } catch (Exception ex) {
            log.error("Error syncing user blocked status for {}: {}", username, ex.getMessage(), ex);
        }
    }
}
