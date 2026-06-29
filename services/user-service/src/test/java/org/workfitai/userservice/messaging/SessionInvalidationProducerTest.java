package org.workfitai.userservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.workfitai.userservice.dto.kafka.SessionInvalidationEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionInvalidationProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    SessionInvalidationProducer producer;

    @Test
    void publishSessionInvalidation_sendsEventWithCorrectFields() {
        UUID userId = UUID.randomUUID();
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishSessionInvalidation(userId, "user1", "BLOCKED");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("session-invalidation-events"), eq(userId.toString()), payloadCaptor.capture());

        SessionInvalidationEvent event = (SessionInvalidationEvent) payloadCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getUsername()).isEqualTo("user1");
        assertThat(event.getReason()).isEqualTo("BLOCKED");
        assertThat(event.getEventType()).isEqualTo("SESSION_INVALIDATION");
        assertThat(event.getEventId()).isNotBlank();
    }

    @Test
    void publishSessionInvalidation_kafkaThrows_swallowsException() {
        UUID userId = UUID.randomUUID();
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenThrow(new RuntimeException("Kafka unavailable"));

        // Should not propagate — fire-and-forget
        producer.publishSessionInvalidation(userId, "user1", "DELETED");
    }
}
