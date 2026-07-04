package org.workfitai.jobservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.workfitai.jobservice.model.dto.kafka.AuditEvent;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks
    private AuditEventPublisher auditEventPublisher;

    private AuditEvent event() {
        return new AuditEvent("evt-1", "job-service", "alice", "HR", "C-A", "JOB", "job-1", "JOB_CREATED",
                null, null, Instant.now(), true, null, "127.0.0.1");
    }

    @Test
    void publish_sendsToAuditEventsTopicWithEntityPartitionKey() {
        AuditEvent event = event();

        auditEventPublisher.publish(event);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("audit-events"), eq("JOB:job-1"), captor.capture());
        assertThat(captor.getValue()).isSameAs(event);
    }

    @Test
    void publish_neverThrows_whenKafkaSendFails() {
        doThrow(new RuntimeException("kafka down")).when(kafkaTemplate).send(any(), any(), any());

        auditEventPublisher.publish(event());
    }
}
