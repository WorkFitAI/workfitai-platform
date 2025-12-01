package org.workfitai.applicationservice.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.constants.Messages;
import org.workfitai.applicationservice.dto.kafka.ApplicationCreatedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationStatusChangedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationWithdrawnEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Kafka producer for publishing application events.
 * 
 * NOTE: Kafka is temporarily disabled. Events are logged but not sent.
 * TODO: Re-enable Kafka when other services are ready to consume events.
 */
@Service
@Slf4j
public class ApplicationEventProducer {

    // Temporarily disabled - uncomment when Kafka consumers are available
    // private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.application-events:application-events}")
    private String applicationEventsTopic;

    @Value("${app.kafka.topics.application-status:application-status}")
    private String applicationStatusTopic;

    public void publishApplicationCreatedEvent(ApplicationCreatedEvent event) {
        String applicationId = event.getData().getApplicationId();
        log.info(Messages.Log.CREATING_APPLICATION, event.getData().getUsername(), event.getData().getJobId());

        // Temporarily disabled - just log the event
        log.info("[KAFKA-DISABLED] Would publish {} to topic '{}' with key '{}'",
                Messages.Kafka.EVENT_APPLICATION_CREATED, applicationEventsTopic, applicationId);
        log.debug("[KAFKA-DISABLED] Event payload: {}", event);
    }

    public void publishStatusChangedEvent(ApplicationStatusChangedEvent event) {
        String applicationId = event.getData().getApplicationId();
        log.info(Messages.Log.UPDATING_STATUS, applicationId, event.getData().getNewStatus());

        // Temporarily disabled - just log the event
        log.info("[KAFKA-DISABLED] Would publish {} to topic '{}' with key '{}'",
                Messages.Kafka.EVENT_STATUS_CHANGED, applicationStatusTopic, applicationId);
        log.debug("[KAFKA-DISABLED] Event payload: {}", event);
    }

    public void publishApplicationWithdrawnEvent(ApplicationWithdrawnEvent event) {
        String applicationId = event.getData().getApplicationId();
        log.info(Messages.Log.WITHDRAWING_APPLICATION, event.getData().getUsername(), applicationId);

        // Temporarily disabled - just log the event
        log.info("[KAFKA-DISABLED] Would publish {} to topic '{}' with key '{}'",
                Messages.Kafka.EVENT_APPLICATION_WITHDRAWN, applicationEventsTopic, applicationId);
        log.debug("[KAFKA-DISABLED] Event payload: {}", event);
    }

    /*
     * TODO: Re-enable when Kafka consumers are ready
     * 
     * private void sendEvent(String topic, String key, Object event, String
     * eventTypeName) {
     * try {
     * CompletableFuture<SendResult<String, Object>> future =
     * kafkaTemplate.send(topic, key, event);
     * future.whenComplete((result, throwable) -> {
     * if (throwable != null) {
     * log.error(Messages.Log.KAFKA_SEND_FAILED, eventTypeName, key,
     * throwable.getMessage(), throwable);
     * } else {
     * log.info(Messages.Log.KAFKA_SEND_SUCCESS,
     * eventTypeName, key,
     * result.getRecordMetadata().topic(),
     * result.getRecordMetadata().offset());
     * }
     * });
     * } catch (Exception ex) {
     * log.error(Messages.Log.KAFKA_SEND_FAILED, eventTypeName, key,
     * ex.getMessage(), ex);
     * }
     * }
     */
}
