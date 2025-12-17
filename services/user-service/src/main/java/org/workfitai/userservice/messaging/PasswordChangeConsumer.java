package org.workfitai.userservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.userservice.dto.kafka.PasswordChangeEvent;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.repository.UserRepository;

import java.util.UUID;

/**
 * Kafka consumer for password change events from auth-service.
 * Syncs passwordHash from auth-service (MongoDB) to user-service (PostgreSQL).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordChangeConsumer {

    private final UserRepository userRepository;

    @KafkaListener(topics = "${app.kafka.topics.password-change:password-change}", groupId = "${spring.kafka.consumer.group-id:user-service-group}", containerFactory = "retryableKafkaListenerContainerFactory")
    @Transactional
    public void handlePasswordChange(
            @Payload PasswordChangeEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("📩 Received password change event from topic: {}, partition: {}, offset: {}",
                topic, partition, offset);
        log.debug("Event details: {}", event);

        try {
            // Validate event
            if (event == null || event.getPasswordData() == null) {
                log.error("❌ Invalid password change event: {}", event);
                acknowledgment.acknowledge();
                return;
            }

            if (!"PASSWORD_CHANGED".equals(event.getEventType())) {
                log.warn("⚠️ Unknown event type: {}, skipping", event.getEventType());
                acknowledgment.acknowledge();
                return;
            }

            var passwordData = event.getPasswordData();
            log.info("🔐 Processing password change for user: {} (userId: {}, reason: {})",
                    passwordData.getUsername(),
                    passwordData.getUserId(),
                    passwordData.getChangeReason());

            // Find user by userId (MongoDB _id maps to PostgreSQL userId)
            UUID userId;
            try {
                userId = UUID.fromString(passwordData.getUserId());
            } catch (IllegalArgumentException e) {
                log.error("❌ Invalid userId format: {}", passwordData.getUserId());
                acknowledgment.acknowledge();
                return;
            }

            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.error("❌ User not found in user-service: userId={}, username={}",
                        userId, passwordData.getUsername());
                acknowledgment.acknowledge();
                return;
            }

            // Update passwordHash
            String oldPasswordHash = user.getPasswordHash();
            user.setPasswordHash(passwordData.getNewPasswordHash());
            userRepository.save(user);

            log.info("✅ Successfully synced password for user: {} (userId: {}). Reason: {}",
                    passwordData.getUsername(),
                    userId,
                    passwordData.getChangeReason());

            log.debug("Password hash changed from {} to {}",
                    oldPasswordHash.substring(0, 10) + "...",
                    passwordData.getNewPasswordHash().substring(0, 10) + "...");

            // Acknowledge successful processing
            acknowledgment.acknowledge();

        } catch (Exception ex) {
            log.error("❌ Error processing password change event: {}", ex.getMessage(), ex);
            // Don't acknowledge - message will be retried
            throw ex;
        }
    }
}
