package org.workfitai.cvservice.messaging;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.workfitai.cvservice.dto.kafka.NotificationEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.notification-events:notification-events}")
    private String notificationTopic;

    public void send(NotificationEvent event) {
        try {
            // Set source service for traceability
            if (event.getSourceService() == null) {
                event.setSourceService("cv-service");
            }
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(notificationTopic,
                    event.getRecipientEmail(), event);
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to send notification event {}: {}", event.getEventId(), throwable.getMessage(),
                            throwable);
                } else {
                    log.info("Notification event {} sent to topic {} offset {}", event.getEventId(),
                            result.getRecordMetadata().topic(), result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            log.error("Error sending notification event {}", event.getEventId(), e);
        }
    }
}
