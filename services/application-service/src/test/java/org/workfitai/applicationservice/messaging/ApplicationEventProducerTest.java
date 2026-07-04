package org.workfitai.applicationservice.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.applicationservice.dto.kafka.ApplicationCreatedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationStatusChangedEvent;
import org.workfitai.applicationservice.dto.kafka.JobStatsUpdateEvent;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

@ExtendWith(MockitoExtension.class)
class ApplicationEventProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks ApplicationEventProducer producer;

    @org.junit.jupiter.api.BeforeEach
    void injectTopicNames() {
        ReflectionTestUtils.setField(producer, "applicationEventsTopic", "application-events");
        ReflectionTestUtils.setField(producer, "applicationStatusTopic", "application-status");
        ReflectionTestUtils.setField(producer, "jobStatsUpdateTopic", "job-stats-update");
        ReflectionTestUtils.setField(producer, "notificationEventsTopic", "notification-events");
    }

    // ─── publishApplicationCreated ────────────────────────────────────────────

    @Test
    void publishApplicationCreated_sendsToCorrectTopicWithApplicationIdAsKey() {
        ApplicationCreatedEvent event = ApplicationCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("APPLICATION_CREATED")
                .timestamp(Instant.now())
                .data(ApplicationCreatedEvent.ApplicationData.builder()
                        .applicationId("app-1")
                        .username("user1")
                        .jobId("job-1")
                        .build())
                .build();

        producer.publishApplicationCreated(event);

        verify(kafkaTemplate).send(eq("application-events"), eq("app-1"), eq(event));
    }

    @Test
    void publishApplicationCreated_kafkaFails_doesNotThrow() {
        ApplicationCreatedEvent event = ApplicationCreatedEvent.builder()
                .eventId("e1").eventType("APPLICATION_CREATED").timestamp(Instant.now())
                .data(ApplicationCreatedEvent.ApplicationData.builder()
                        .applicationId("app-1").username("u1").jobId("j1").build())
                .build();

        doThrow(new RuntimeException("Kafka down")).when(kafkaTemplate).send(any(), any(), any());

        producer.publishApplicationCreated(event); // must not throw
    }

    // ─── publishStatusChanged ─────────────────────────────────────────────────

    @Test
    void publishStatusChanged_sendsToStatusTopic() {
        ApplicationStatusChangedEvent event = ApplicationStatusChangedEvent.builder()
                .eventId("e1").eventType("STATUS_CHANGED").timestamp(Instant.now())
                .data(ApplicationStatusChangedEvent.StatusChangeData.builder()
                        .applicationId("app-1").username("u1").jobId("j1")
                        .previousStatus(ApplicationStatus.APPLIED)
                        .newStatus(ApplicationStatus.REVIEWING)
                        .changedBy("hr1").changedAt(Instant.now()).build())
                .build();

        producer.publishStatusChanged(event);

        verify(kafkaTemplate).send(eq("application-status"), eq("app-1"), eq(event));
    }

    @Test
    void publishStatusChanged_kafkaFails_doesNotThrow() {
        ApplicationStatusChangedEvent event = ApplicationStatusChangedEvent.builder()
                .eventId("e1").eventType("STATUS_CHANGED").timestamp(Instant.now())
                .data(ApplicationStatusChangedEvent.StatusChangeData.builder()
                        .applicationId("app-1").username("u1").jobId("j1")
                        .previousStatus(ApplicationStatus.APPLIED)
                        .newStatus(ApplicationStatus.REVIEWING)
                        .changedBy("hr1").changedAt(Instant.now()).build())
                .build();

        doThrow(new RuntimeException("Kafka down")).when(kafkaTemplate).send(any(), any(), any());

        producer.publishStatusChanged(event); // must not throw
    }

    // ─── publishJobStatsUpdate ────────────────────────────────────────────────

    @Test
    void publishJobStatsUpdate_sendsToJobStatsTopic() {
        UUID jobId = UUID.randomUUID();
        JobStatsUpdateEvent event = JobStatsUpdateEvent.builder()
                .eventId("e1").jobId(jobId).totalApplications(5)
                .timestamp(Instant.now()).operation("INCREMENT").build();

        producer.publishJobStatsUpdate(event);

        verify(kafkaTemplate).send(eq("job-stats-update"), eq(jobId.toString()), eq(event));
    }

    // ─── publishCandidateNotification ─────────────────────────────────────────

    @Test
    void publishCandidateNotification_sendsToNotificationTopic() {
        producer.publishCandidateNotification(
                "app-1", "user@example.com", "user1", "Java Dev", "Acme", Instant.now());

        verify(kafkaTemplate).send(eq("notification-events"), eq("app-1-candidate"), any());
    }

    @Test
    void publishCandidateNotification_kafkaFails_doesNotThrow() {
        doThrow(new RuntimeException("Kafka down")).when(kafkaTemplate).send(any(), any(), any());

        producer.publishCandidateNotification(
                "app-1", "user@example.com", "user1", "Java Dev", "Acme", Instant.now());
    }

    // ─── publishHrNotification ────────────────────────────────────────────────

    @Test
    void publishHrNotification_sendsToNotificationTopicWithHrSuffix() {
        producer.publishHrNotification(
                "app-1", "hr@example.com", "hr_user", "John Doe", "Java Dev", "Acme", Instant.now());

        verify(kafkaTemplate).send(eq("notification-events"), eq("app-1-hr"), any());
    }
}
