package org.workfitai.authservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.workfitai.authservice.dto.kafka.PasswordChangeEvent;

@ExtendWith(MockitoExtension.class)
class PasswordChangeProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks PasswordChangeProducer producer;

    private PasswordChangeEvent buildEvent() {
        return PasswordChangeEvent.builder()
                .eventId("evt-pw-1")
                .eventType("PASSWORD_CHANGED")
                .timestamp(Instant.now())
                .passwordData(PasswordChangeEvent.PasswordData.builder()
                        .userId("user-id-1")
                        .username("alice")
                        .email("alice@example.com")
                        .newPasswordHash("new-hash")
                        .passwordChangedAt(Instant.now())
                        .changeReason("USER_CHANGE")
                        .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    @Test
    void publishPasswordChangeEvent_success_sendsToKafka() {
        PasswordChangeEvent event = buildEvent();

        SendResult<String, Object> sendResult = org.mockito.Mockito.mock(SendResult.class);

        when(kafkaTemplate.send(any(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        producer.publishPasswordChangeEvent(event);

        verify(kafkaTemplate).send(any(), eq("user-id-1"), eq(event));
    }

    @Test
    void publishPasswordChangeEvent_kafkaFails_doesNotThrow() {
        PasswordChangeEvent event = buildEvent();

        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka unavailable"));

        when(kafkaTemplate.send(any(), anyString(), any())).thenReturn(failed);

        // fire-and-forget pattern — must not propagate the exception
        producer.publishPasswordChangeEvent(event);

        verify(kafkaTemplate).send(any(), eq("user-id-1"), eq(event));
    }
}
