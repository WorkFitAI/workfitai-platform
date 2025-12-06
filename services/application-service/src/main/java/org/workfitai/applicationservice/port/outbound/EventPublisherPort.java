package org.workfitai.applicationservice.port.outbound;

import org.workfitai.applicationservice.dto.kafka.ApplicationCreatedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationStatusChangedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationWithdrawnEvent;

/**
 * Outbound port for event publishing.
 * Part of Hexagonal Architecture - defines interface for infrastructure
 * adapter.
 * 
 * Implementation: ApplicationEventProducer (publishes to Kafka or logs if
 * disabled)
 * 
 * Pattern: Fire-and-Forget
 * Events are published asynchronously without waiting for acknowledgment.
 * Consumers (job-service, notification-service) handle events independently.
 */
public interface EventPublisherPort {

    /**
     * Publish event when a new application is created.
     * Consumers: job-service (update application count), notification-service
     * (notify HR)
     *
     * @param event The application created event
     */
    void publishApplicationCreated(ApplicationCreatedEvent event);

    /**
     * Publish event when application status changes.
     * Consumers: notification-service (notify candidate of status update)
     *
     * @param event The status changed event
     */
    void publishStatusChanged(ApplicationStatusChangedEvent event);

    /**
     * Publish event when an application is withdrawn.
     * Consumers: job-service (update application count), notification-service
     *
     * @param event The withdrawal event
     */
    void publishApplicationWithdrawn(ApplicationWithdrawnEvent event);
}
