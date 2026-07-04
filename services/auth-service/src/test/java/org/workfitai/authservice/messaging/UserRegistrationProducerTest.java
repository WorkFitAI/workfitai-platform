package org.workfitai.authservice.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

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
import org.workfitai.authservice.dto.kafka.UserRegistrationEvent;

@ExtendWith(MockitoExtension.class)
class UserRegistrationProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks UserRegistrationProducer producer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(producer, "userRegistrationTopic", "user-registration");
    }

    private UserRegistrationEvent event(String email) {
        return UserRegistrationEvent.builder()
                .eventId("evt-reg-001")
                .eventType("USER_REGISTERED")
                .userData(UserRegistrationEvent.UserData.builder()
                        .email(email)
                        .username("alice")
                        .role("CANDIDATE")
                        .status("PENDING")
                        .build())
                .build();
    }

    private CompletableFuture<SendResult<String, Object>> successFuture() {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("user-registration", 0), 1L, 0, 0L, 0, 0);
        SendResult<String, Object> sendResult = new SendResult<>(
                new ProducerRecord<>("user-registration", "key", "val"), metadata);
        return CompletableFuture.completedFuture(sendResult);
    }

    // ─── publishUserRegistrationEvent (sync) ──────────────────────────────────

    @Test
    void publishUserRegistrationEvent_sendsToKafkaTopic() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

        producer.publishUserRegistrationEvent(event("alice@example.com"));

        verify(kafkaTemplate).send(eq("user-registration"), eq("alice@example.com"), any());
    }

    @Test
    void publishUserRegistrationEvent_throwsRuntimeException_onKafkaFailure() {
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture);

        assertThatThrownBy(() -> producer.publishUserRegistrationEvent(event("alice@example.com")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to publish user registration event");
    }

    // ─── publishUserRegistrationEventAsync ────────────────────────────────────

    @Test
    void publishUserRegistrationEventAsync_sendsWithoutWaiting() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture());

        producer.publishUserRegistrationEventAsync(event("alice@example.com"));

        verify(kafkaTemplate).send(eq("user-registration"), eq("alice@example.com"), any());
    }

    @Test
    void publishUserRegistrationEventAsync_doesNotThrow_onKafkaFailure() {
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture);

        producer.publishUserRegistrationEventAsync(event("alice@example.com")); // must not throw
    }
}
