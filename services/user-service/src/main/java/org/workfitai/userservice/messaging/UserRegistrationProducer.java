package org.workfitai.userservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.workfitai.userservice.dto.kafka.UserRegistrationEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer for publishing user registration/status update events.
 * Used to sync user status changes back to auth-service after approval.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.user-registration:user-registration}")
    private String userRegistrationTopic;

    /**
     * Publish user registration event to Kafka.
     * This is used to sync status changes (e.g., approval) back to auth-service.
     */
    public void publishUserRegistrationEvent(UserRegistrationEvent event) {
        try {
            String key = event.getUserData() != null ? event.getUserData().getEmail() : event.getEventId();

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(userRegistrationTopic, key,
                    event);

            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to publish user registration event {} to topic {}",
                            event.getEventId(), userRegistrationTopic, throwable);
                } else {
                    log.info("User registration event {} (type: {}) published to topic {} partition {} offset {}",
                            event.getEventId(),
                            event.getEventType(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (Exception ex) {
            log.error("Error publishing user registration event {}", event.getEventId(), ex);
        }
    }
}
