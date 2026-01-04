package org.workfitai.authservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.workfitai.authservice.dto.kafka.UserChangeEvent;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.repository.UserSessionRepository;
import org.workfitai.authservice.service.RefreshTokenService;

import java.time.Instant;
import java.util.Optional;

/**
 * Kafka consumer for user block/unblock/delete events from user-service.
 * Syncs blocked status from user-service to auth-service and invalidates
 * sessions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserBlockSyncConsumer {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final RefreshTokenService refreshTokenService;

    @KafkaListener(topics = "user-change-events", groupId = "auth-service-block-sync", containerFactory = "userChangeEventListenerContainerFactory")
    public void handleUserChangeEvent(@Payload UserChangeEvent event) {
        if (event == null || event.getData() == null) {
            log.warn("Received null or invalid user change event");
            return;
        }

        String eventType = event.getEventType();
        log.info("Received user change event: type={}, userId={}", eventType, event.getData().getUserId());

        // Process BLOCKED, UNBLOCKED, and DELETED events
        if (!"USER_BLOCKED".equals(eventType) &&
                !"USER_UNBLOCKED".equals(eventType) &&
                !"USER_DELETED".equals(eventType)) {
            log.debug("Ignoring event type: {} (not a block/unblock/delete event)", eventType);
            return;
        }

        var userData = event.getData();
        String username = userData.getUsername();
        String userId = userData.getUserId().toString();

        try {
            Optional<User> userOpt = userRepository.findByUsername(username);

            if (userOpt.isEmpty()) {
                log.warn("User not found in auth-service: {}", username);
                return;
            }

            User user = userOpt.get();

            // Handle USER_DELETED event
            if ("USER_DELETED".equals(eventType)) {
                // Invalidate all sessions and refresh tokens
                sessionRepository.deleteByUserId(userId);
                refreshTokenService.deleteAllByUserId(userId);
                log.info("Deleted all sessions and refresh tokens for deleted user: {}", username);
                return;
            }

            // Handle USER_BLOCKED event
            if ("USER_BLOCKED".equals(eventType)) {
                boolean shouldBeBlocked = true;

                if (Boolean.TRUE.equals(user.getIsBlocked()) == shouldBeBlocked) {
                    log.info("User {} already blocked, skipping update", username);
                    return;
                }

                user.setIsBlocked(shouldBeBlocked);
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);

                // Invalidate all sessions and refresh tokens when user is blocked
                sessionRepository.deleteByUserId(userId);
                refreshTokenService.deleteAllByUserId(userId);
                log.info("Successfully blocked user {} and deleted all sessions", username);
                return;
            }

            // Handle USER_UNBLOCKED event
            if ("USER_UNBLOCKED".equals(eventType)) {
                boolean shouldBeBlocked = false;

                if (Boolean.TRUE.equals(user.getIsBlocked()) == shouldBeBlocked) {
                    log.info("User {} already unblocked, skipping update", username);
                    return;
                }

                user.setIsBlocked(shouldBeBlocked);
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);
                log.info("Successfully unblocked user {}", username);
            }

        } catch (Exception ex) {
            log.error("Error processing user change event for {}: {}", username, ex.getMessage(), ex);
        }
    }
}
