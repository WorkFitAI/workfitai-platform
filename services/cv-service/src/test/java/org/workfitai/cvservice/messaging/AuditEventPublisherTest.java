package org.workfitai.cvservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.workfitai.cvservice.dto.kafka.AuditEvent;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private AuditEventPublisher publisher;

    private void init() {
        publisher = new AuditEventPublisher(kafkaTemplate);
    }

    private AuditEvent sampleEvent() {
        return new AuditEvent("evt-1", "cv-service", "alice", "CANDIDATE", null,
                "CV", "cv-1", "CV_CREATED", null, null, Instant.now(), true, null, "127.0.0.1");
    }

    @SuppressWarnings("unchecked")
    @Test
    void publish_sendsToAuditEventsTopic_withEntityTypeAndIdAsPartitionKey() {
        init();
        AuditEvent event = sampleEvent();

        publisher.publish(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), any(AuditEvent.class));
        assertThat(topicCaptor.getValue()).isEqualTo("audit-events");
        assertThat(keyCaptor.getValue()).isEqualTo("CV:cv-1");
    }

    @Test
    void publish_swallowsException_whenKafkaTemplateThrows() {
        init();
        doThrow(new RuntimeException("kafka down")).when(kafkaTemplate).send(any(String.class), any(), any());

        publisher.publish(sampleEvent());
    }
}
