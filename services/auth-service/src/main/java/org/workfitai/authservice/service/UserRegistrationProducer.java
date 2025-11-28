package org.workfitai.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.workfitai.authservice.dto.kafka.UserRegistrationEvent;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.user-registration:user-registration}")
    private String userRegistrationTopic;

    public void publishUserRegistrationEvent(UserRegistrationEvent event) {
        log.info("Publishing user registration event for email: {}", event.getUserData().getEmail());

        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(userRegistrationTopic,
                    event.getUserData().getEmail(), event);

            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to send user registration event for email: {}. Error: {}",
                            event.getUserData().getEmail(), throwable.getMessage(), throwable);
                } else {
                    log.info("Successfully sent user registration event for email: {} to topic: {} at offset: {}",
                            event.getUserData().getEmail(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().offset());
                }
            });

        } catch (Exception ex) {
            log.error("Error publishing user registration event for email: {}. Error: {}",
                    event.getUserData().getEmail(), ex.getMessage(), ex);
        }
    }
}