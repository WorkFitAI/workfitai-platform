package org.workfitai.applicationservice.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.dto.kafka.ApplicationCreatedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationStatusChangedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationWithdrawnEvent;
import org.workfitai.applicationservice.port.outbound.EventPublisherPort;

import lombok.extern.slf4j.Slf4j;

/**
 * Kafka producer for publishing application events.
 * Implements EventPublisherPort for Hexagonal Architecture.
 * 
 * NOTE: Kafka is temporarily disabled. Events are logged but not sent.
 * TODO: Re-enable Kafka when other services are ready to consume events.
 * 
 * Pattern: Fire-and-Forget
 * Events are published asynchronously. Failures are logged but don't
 * affect the main application flow.
 */
@Service
@Slf4j
public class ApplicationEventProducer implements EventPublisherPort {

    // Temporarily disabled - uncomment when Kafka consumers are available
    // private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.application-events:application-events}")
    private String applicationEventsTopic;

    @Value("${app.kafka.topics.application-status:application-status}")
    private String applicationStatusTopic;

    @Override
    public void publishApplicationCreated(ApplicationCreatedEvent event) {
        String applicationId = event.getData().getApplicationId();
        log.info("Publishing APPLICATION_CREATED event: applicationId={}, jobId={}",
                applicationId, event.getData().getJobId());

        // TODO: Re-enable when Kafka consumers are available
        // kafkaTemplate.send(applicationEventsTopic, applicationId, event);

        log.info("[KAFKA-DISABLED] Would publish APPLICATION_CREATED to topic '{}' with key '{}'",
                applicationEventsTopic, applicationId);
        log.debug("[KAFKA-DISABLED] Event payload: {}", event);
    }

    @Override
    public void publishStatusChanged(ApplicationStatusChangedEvent event) {
        String applicationId = event.getData().getApplicationId();
        log.info("Publishing STATUS_CHANGED event: applicationId={}, {} → {}",
                applicationId, event.getData().getPreviousStatus(), event.getData().getNewStatus());

        // TODO: Re-enable when Kafka consumers are available
        // kafkaTemplate.send(applicationStatusTopic, applicationId, event);

        log.info("[KAFKA-DISABLED] Would publish STATUS_CHANGED to topic '{}' with key '{}'",
                applicationStatusTopic, applicationId);
        log.debug("[KAFKA-DISABLED] Event payload: {}", event);
    }

    @Override
    public void publishApplicationWithdrawn(ApplicationWithdrawnEvent event) {
        String applicationId = event.getData().getApplicationId();
        log.info("Publishing APPLICATION_WITHDRAWN event: applicationId={}, username={}",
                applicationId, event.getData().getUsername());

        // TODO: Re-enable when Kafka consumers are available
        // kafkaTemplate.send(applicationEventsTopic, applicationId, event);

        log.info("[KAFKA-DISABLED] Would publish APPLICATION_WITHDRAWN to topic '{}' with key '{}'",
                applicationEventsTopic, applicationId);
        log.debug("[KAFKA-DISABLED] Event payload: {}", event);
    }

    // Legacy methods for backward compatibility (delegate to port methods)

    public void publishApplicationCreatedEvent(ApplicationCreatedEvent event) {
        publishApplicationCreated(event);
    }

    public void publishStatusChangedEvent(ApplicationStatusChangedEvent event) {
        publishStatusChanged(event);
    }

    public void publishApplicationWithdrawnEvent(ApplicationWithdrawnEvent event) {
        publishApplicationWithdrawn(event);
    }
}
