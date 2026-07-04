package org.workfitai.cvservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.cvservice.dto.kafka.NotificationEvent;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private NotificationProducer producer;

    private void init() {
        producer = new NotificationProducer(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "notificationTopic", "notification-events");
    }

    @SuppressWarnings("unchecked")
    @Test
    void send_setsSourceServiceWhenAbsent_andSendsWithRecipientEmailAsKey() {
        init();
        NotificationEvent event = NotificationEvent.builder().eventId("e1").recipientEmail("a@b.com").build();
        SendResult<String, Object> sendResult = mock(SendResult.class, Answers.RETURNS_DEEP_STUBS);
        when(kafkaTemplate.send("notification-events", "a@b.com", event))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        producer.send(event);

        assertThat(event.getSourceService()).isEqualTo("cv-service");
        verify(kafkaTemplate).send("notification-events", "a@b.com", event);
    }

    @SuppressWarnings("unchecked")
    @Test
    void send_keepsExistingSourceService_whenAlreadySet() {
        init();
        NotificationEvent event = NotificationEvent.builder()
                .eventId("e1").recipientEmail("a@b.com").sourceService("other-service").build();
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class, Answers.RETURNS_DEEP_STUBS)));

        producer.send(event);

        assertThat(event.getSourceService()).isEqualTo("other-service");
    }

    @Test
    void send_swallowsException_whenKafkaTemplateThrowsSynchronously() {
        init();
        NotificationEvent event = NotificationEvent.builder().eventId("e1").recipientEmail("a@b.com").build();
        when(kafkaTemplate.send(any(), any(), any())).thenThrow(new RuntimeException("boom"));

        producer.send(event);
    }

    @SuppressWarnings("unchecked")
    @Test
    void send_logsError_whenFutureCompletesExceptionally() {
        init();
        NotificationEvent event = NotificationEvent.builder().eventId("e1").recipientEmail("a@b.com").build();
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);

        producer.send(event);
    }
}
