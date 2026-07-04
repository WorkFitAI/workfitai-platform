package org.workfitai.authservice.messaging;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.workfitai.authservice.dto.kafka.AuditEvent;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks AuditEventPublisher publisher;

    private AuditEvent buildEvent(String entityType, String entityId, String action) {
        return new AuditEvent(
                "evt-id-123",
                "auth-service",
                "alice",
                "CANDIDATE",
                null,
                entityType,
                entityId,
                action,
                null,
                null,
                Instant.now(),
                true,
                null,
                "127.0.0.1"
        );
    }

    @Test
    void publish_sendsToAuditEventsTopic() {
        // Arrange
        AuditEvent event = buildEvent("USER", "alice", "AUTH_LOGIN_SUCCESS");

        // Act
        publisher.publish(event);

        // Assert — topic name and partition key are verified
        verify(kafkaTemplate).send(eq("audit-events"), eq("USER:alice"), eq(event));
    }

    @Test
    void publish_usesEntityTypeAndEntityIdAsPartitionKey() {
        // Arrange
        AuditEvent event = buildEvent("SESSION", "session-abc", "AUTH_LOGOUT");

        // Act
        publisher.publish(event);

        // Assert
        verify(kafkaTemplate).send(eq("audit-events"), eq("SESSION:session-abc"), eq(event));
    }

    @Test
    void publish_kafkaExceptionDoesNotPropagate() {
        // Arrange
        AuditEvent event = buildEvent("USER", "alice", "AUTH_LOGIN_SUCCESS");
        org.mockito.Mockito.doThrow(new RuntimeException("Kafka down"))
                .when(kafkaTemplate).send(eq("audit-events"), eq("USER:alice"), eq(event));

        // Act — should not throw; fire-and-forget
        publisher.publish(event);
    }
}
