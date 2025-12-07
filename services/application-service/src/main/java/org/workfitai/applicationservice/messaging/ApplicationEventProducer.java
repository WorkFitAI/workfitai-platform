package org.workfitai.applicationservice.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.dto.kafka.ApplicationCreatedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationStatusChangedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationWithdrawnEvent;
import org.workfitai.applicationservice.port.outbound.EventPublisherPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka producer for publishing application events.
 * Implements EventPublisherPort for Hexagonal Architecture.
 *
 * Pattern: Fire-and-Forget
 * Events are published asynchronously. Failures are logged but don't
 * affect the main application flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationEventProducer implements EventPublisherPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.application-events:application-events}")
    private String applicationEventsTopic;

    @Value("${app.kafka.topics.application-status:application-status}")
    private String applicationStatusTopic;

    @Override
    public void publishApplicationCreated(ApplicationCreatedEvent event) {
        String applicationId = event.getData().getApplicationId();
        log.info("Publishing APPLICATION_CREATED event: applicationId={}, jobId={}",
                applicationId, event.getData().getJobId());

        try {
            kafkaTemplate.send(applicationEventsTopic, applicationId, event);
            log.debug("APPLICATION_CREATED event published to topic '{}' with key '{}'",
                    applicationEventsTopic, applicationId);
        } catch (Exception e) {
            log.error("Failed to publish APPLICATION_CREATED event (non-critical): {}", e.getMessage());
        }
    }

    @Override
    public void publishStatusChanged(ApplicationStatusChangedEvent event) {
        String applicationId = event.getData().getApplicationId();
        log.info("Publishing STATUS_CHANGED event: applicationId={}, {} → {}",
                applicationId, event.getData().getPreviousStatus(), event.getData().getNewStatus());

        try {
            kafkaTemplate.send(applicationStatusTopic, applicationId, event);
            log.debug("STATUS_CHANGED event published to topic '{}' with key '{}'",
                    applicationStatusTopic, applicationId);
        } catch (Exception e) {
            log.error("Failed to publish STATUS_CHANGED event (non-critical): {}", e.getMessage());
        }
    }

    @Override
    public void publishApplicationWithdrawn(ApplicationWithdrawnEvent event) {
        String applicationId = event.getData().getApplicationId();
        log.info("Publishing APPLICATION_WITHDRAWN event: applicationId={}, username={}",
                applicationId, event.getData().getUsername());

        try {
            kafkaTemplate.send(applicationEventsTopic, applicationId, event);
            log.debug("APPLICATION_WITHDRAWN event published to topic '{}' with key '{}'",
                    applicationEventsTopic, applicationId);
        } catch (Exception e) {
            log.error("Failed to publish APPLICATION_WITHDRAWN event (non-critical): {}", e.getMessage());
        }
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
