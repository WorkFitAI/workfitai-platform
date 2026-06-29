package org.workfitai.userservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.workfitai.userservice.dto.kafka.AuditEvent;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks AuditEventPublisher publisher;

    private AuditEvent buildEvent(String entityType, String entityId) {
        return new AuditEvent(
                "evt-001", "user-service", "admin", "ADMIN",
                null, entityType, entityId, "USER_CREATED",
                null, Map.of("email", "test@test.com"),
                Instant.now(), true, null, "127.0.0.1"
        );
    }

    @Test
    void publish_sendsToAuditEventsTopic() {
        AuditEvent event = buildEvent("USER", "testuser");
        publisher.publish(event);
        verify(kafkaTemplate).send(eq("audit-events"), eq("USER:testuser"), eq(event));
    }

    @Test
    void publish_partitionKeyIsEntityTypeColonEntityId() {
        AuditEvent event = buildEvent("USER", "jane");
        publisher.publish(event);
        verify(kafkaTemplate).send(anyString(), eq("USER:jane"), any());
    }

    @Test
    void publish_doesNotThrow_whenKafkaFails() {
        AuditEvent event = buildEvent("USER", "bob");
        doThrow(new RuntimeException("broker down")).when(kafkaTemplate).send(anyString(), anyString(), any());
        // Must not propagate the exception
        publisher.publish(event);
    }
}
