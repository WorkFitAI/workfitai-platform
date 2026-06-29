package org.workfitai.applicationservice.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.workfitai.applicationservice.dto.kafka.AuditEvent;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks AuditEventPublisher publisher;

    private AuditEvent sampleEvent() {
        return new AuditEvent(
                "evt-1", "application-service", "hr1", "HR",
                "company-1", "APPLICATION", "app-1", "STATUS_CHANGED",
                null, null, Instant.now(), true, null, "127.0.0.1");
    }

    @Test
    void publish_success_sendsToKafka() {
        AuditEvent event = sampleEvent();

        publisher.publish(event);

        verify(kafkaTemplate).send("audit-events", "APPLICATION:app-1", event);
    }

    @Test
    void publish_kafkaThrows_doesNotPropagate() {
        AuditEvent event = sampleEvent();
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka broker down"));

        assertDoesNotThrow(() -> publisher.publish(event));
    }
}
