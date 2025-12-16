package org.workfitai.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.workfitai.authservice.dto.kafka.PasswordChangeEvent;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordChangeProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.password-change:password-change}")
    private String passwordChangeTopic;

    /**
     * Publish password change event asynchronously.
     * Fire-and-forget pattern: does not block password change operation.
     * If Kafka fails, password change still succeeds in auth-service.
     * User-service will be out of sync until next sync operation.
     */
    public void publishPasswordChangeEvent(PasswordChangeEvent event) {
        log.info("Publishing password change event for user: {} (userId: {})",
                event.getPasswordData().getUsername(),
                event.getPasswordData().getUserId());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                passwordChangeTopic,
                event.getPasswordData().getUserId(), // Use userId as key for partitioning
                event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("❌ Failed to send password change event for user: {} (userId: {}). Error: {}",
                        event.getPasswordData().getUsername(),
                        event.getPasswordData().getUserId(),
                        ex.getMessage());
            } else {
                log.info(
                        "✅ Successfully sent password change event for user: {} (userId: {}) to topic: {} at offset: {}",
                        event.getPasswordData().getUsername(),
                        event.getPasswordData().getUserId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
