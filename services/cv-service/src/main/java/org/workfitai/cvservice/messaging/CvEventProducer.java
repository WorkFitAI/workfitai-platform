package org.workfitai.cvservice.messaging;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.workfitai.cvservice.dto.kafka.CvUpdatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CvEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.cv-events:cv.updated}")
    private String cvEventsTopic;

    public void sendCvUpdated(CvUpdatedEvent event) {
        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(cvEventsTopic, event.getUsername(), event);
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to send cv.updated event {}: {}", event.getEventId(), throwable.getMessage(),
                            throwable);
                } else {
                    log.info("cv.updated event {} sent to topic {} offset {}", event.getEventId(),
                            result.getRecordMetadata().topic(), result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            log.error("Error sending cv.updated event {}", event.getEventId(), e);
        }
    }
}
