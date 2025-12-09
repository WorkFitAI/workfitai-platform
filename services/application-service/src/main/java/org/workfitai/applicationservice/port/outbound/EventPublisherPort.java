package org.workfitai.applicationservice.port.outbound;

import org.workfitai.applicationservice.dto.kafka.ApplicationCreatedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationStatusChangedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationWithdrawnEvent;
import org.workfitai.applicationservice.dto.kafka.JobStatsUpdateEvent;

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

    /**
     * Publish event to update job statistics.
     * Consumer: job-service (update totalApplications count)
     *
     * @param event The job stats update event
     */
    void publishJobStatsUpdate(JobStatsUpdateEvent event);

    /**
     * Publish candidate notification event to unified notification-events topic.
     *
     * @param applicationId  Application ID
     * @param candidateEmail Candidate email address
     * @param candidateName  Candidate full name
     * @param jobTitle       Job title
     * @param companyName    Company name
     * @param appliedAt      Application timestamp
     */
    void publishCandidateNotification(String applicationId, String candidateEmail, String candidateName,
            String jobTitle, String companyName, java.time.Instant appliedAt);

    /**
     * Publish HR notification event to unified notification-events topic.
     *
     * @param applicationId Application ID
     * @param hrEmail       HR email address
     * @param hrName        HR full name
     * @param candidateName Candidate full name
     * @param jobTitle      Job title
     * @param companyName   Company name
     * @param appliedAt     Application timestamp
     */
    void publishHrNotification(String applicationId, String hrEmail, String hrName,
            String candidateName, String jobTitle, String companyName, java.time.Instant appliedAt);
}
