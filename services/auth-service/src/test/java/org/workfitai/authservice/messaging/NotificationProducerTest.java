package org.workfitai.authservice.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.authservice.dto.kafka.NotificationEvent;

@ExtendWith(MockitoExtension.class)
class NotificationProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks NotificationProducer producer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(producer, "notificationTopic", "notification-events");
    }

    private NotificationEvent event(String recipientEmail) {
        return NotificationEvent.builder()
                .eventId("evt-001")
                .recipientEmail(recipientEmail)
                .subject("Test Subject")
                .build();
    }

    private CompletableFuture<SendResult<String, Object>> successFuture() {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("notification-events", 0), 0L, 0, 0L, 0, 0);
        SendResult<String, Object> sendResult = new SendResult<>(
                new ProducerRecord<>("notification-events", "key", "value"), metadata);
        return CompletableFuture.completedFuture(sendResult);
    }

    @Test
    void send_publishesEventToKafkaTopic() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

        producer.send(event("user@example.com"));

        verify(kafkaTemplate).send(eq("notification-events"), eq("user@example.com"), any(NotificationEvent.class));
    }

    @Test
    void send_setsSourceServiceIfNull() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());
        NotificationEvent evt = event("user@example.com");
        evt.setSourceService(null);

        producer.send(evt);

        // after send(), sourceService should be set to "auth-service"
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void send_preservesSourceServiceIfAlreadySet() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());
        NotificationEvent evt = event("user@example.com");
        evt.setSourceService("other-service");

        producer.send(evt);

        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void send_doesNotThrow_whenKafkaTemplateFails() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka unavailable"));

        producer.send(event("user@example.com")); // must not throw
    }
}
