package org.workfitai.applicationservice.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.dto.kafka.ApplicationCreatedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationStatusChangedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationWithdrawnEvent;
import org.workfitai.applicationservice.dto.kafka.JobStatsUpdateEvent;
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

    @Value("${app.kafka.topics.job-stats-update:job-stats-update}")
    private String jobStatsUpdateTopic;

    @Value("${app.kafka.topics.notification-events:application-notification-events}")
    private String notificationEventsTopic;

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

    @Override
    public void publishJobStatsUpdate(JobStatsUpdateEvent event) {
        String jobId = event.getJobId().toString();
        log.info("Publishing JOB_STATS_UPDATE event: jobId={}, totalApplications={}",
                jobId, event.getTotalApplications());

        try {
            kafkaTemplate.send(jobStatsUpdateTopic, jobId, event);
            log.debug("JOB_STATS_UPDATE event published to topic '{}' with key '{}'",
                    jobStatsUpdateTopic, jobId);
        } catch (Exception e) {
            log.error("Failed to publish JOB_STATS_UPDATE event (non-critical): {}", e.getMessage());
        }
    }

    @Override
    public void publishCandidateNotification(String applicationId, String candidateEmail, String candidateName,
            String jobTitle, String companyName, java.time.Instant appliedAt) {
        log.info("Publishing candidate notification: applicationId={}, email={}", applicationId, candidateEmail);

        try {
            // Build NotificationEvent for candidate
            Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("candidateName", candidateName);
            metadata.put("jobTitle", jobTitle);
            metadata.put("companyName", companyName);
            metadata.put("applicationId", applicationId);
            metadata.put("appliedAt", appliedAt.toString());

            Map<String, Object> notificationEvent = new java.util.HashMap<>();
            notificationEvent.put("eventId", UUID.randomUUID().toString());
            notificationEvent.put("eventType", "APPLICATION_SUBMITTED");
            notificationEvent.put("timestamp", Instant.now().toString());
            notificationEvent.put("recipientEmail", candidateEmail);
            notificationEvent.put("recipientRole", "CANDIDATE");
            notificationEvent.put("subject", "Application Submitted: " + jobTitle);
            notificationEvent.put("templateType", "APPLICATION_CONFIRMATION");
            notificationEvent.put("sendEmail", true);
            notificationEvent.put("createInAppNotification", false);
            notificationEvent.put("referenceId", applicationId);
            notificationEvent.put("referenceType", "APPLICATION");
            notificationEvent.put("sourceService", "application-service");
            notificationEvent.put("metadata", metadata);

            kafkaTemplate.send(notificationEventsTopic, applicationId + "-candidate", notificationEvent);
            log.debug("Candidate notification published to topic '{}'", notificationEventsTopic);
        } catch (Exception e) {
            log.error("Failed to publish candidate notification (non-critical): {}", e.getMessage());
        }
    }

    @Override
    public void publishHrNotification(String applicationId, String hrEmail, String hrName,
            String candidateName, String jobTitle, String companyName, java.time.Instant appliedAt) {
        log.info("Publishing HR notification: applicationId={}, email={}", applicationId, hrEmail);

        try {
            // Build NotificationEvent for HR
            Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("hrName", hrName);
            metadata.put("candidateName", candidateName);
            metadata.put("jobTitle", jobTitle);
            metadata.put("companyName", companyName);
            metadata.put("applicationId", applicationId);
            metadata.put("appliedAt", appliedAt.toString());

            Map<String, Object> notificationEvent = new java.util.HashMap<>();
            notificationEvent.put("eventId", UUID.randomUUID().toString());
            notificationEvent.put("eventType", "NEW_APPLICATION");
            notificationEvent.put("timestamp", Instant.now().toString());
            notificationEvent.put("recipientEmail", hrEmail);
            notificationEvent.put("recipientRole", "HR");
            notificationEvent.put("subject", "New Application: " + jobTitle);
            notificationEvent.put("templateType", "NEW_APPLICATION_HR");
            notificationEvent.put("sendEmail", true);
            notificationEvent.put("createInAppNotification", false);
            notificationEvent.put("referenceId", applicationId);
            notificationEvent.put("referenceType", "APPLICATION");
            notificationEvent.put("sourceService", "application-service");
            notificationEvent.put("metadata", metadata);

            kafkaTemplate.send(notificationEventsTopic, applicationId + "-hr", notificationEvent);
            log.debug("HR notification published to topic '{}'", notificationEventsTopic);
        } catch (Exception e) {
            log.error("Failed to publish HR notification (non-critical): {}", e.getMessage());
        }
    }
}
