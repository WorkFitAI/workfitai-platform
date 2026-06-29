package org.workfitai.jobservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.workfitai.jobservice.dto.kafka.NotificationEvent;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks
    private NotificationProducer notificationProducer;

    @Test
    void send_setsSourceServiceAndSendsToKafka_whenNotSet() {
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(new CompletableFuture<>());
        NotificationEvent event = NotificationEvent.builder()
                .eventId("evt-1").recipientEmail("hr@test.com").build();

        notificationProducer.send(event);

        assertThat(event.getSourceService()).isEqualTo("job-service");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(any(), org.mockito.ArgumentMatchers.eq("hr@test.com"), captor.capture());
        assertThat(captor.getValue()).isSameAs(event);
    }

    @Test
    void send_preservesExistingSourceService() {
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(new CompletableFuture<>());
        NotificationEvent event = NotificationEvent.builder()
                .eventId("evt-1").recipientEmail("hr@test.com").sourceService("cv-service").build();

        notificationProducer.send(event);

        assertThat(event.getSourceService()).isEqualTo("cv-service");
    }

    @Test
    void send_logsErrorOnFutureFailure_withoutThrowing() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);
        NotificationEvent event = NotificationEvent.builder().eventId("evt-1").recipientEmail("hr@test.com").build();

        notificationProducer.send(event);
        future.completeExceptionally(new RuntimeException("kafka down"));
    }

    @Test
    void send_neverThrows_whenKafkaTemplateThrowsSynchronously() {
        doThrow(new RuntimeException("kafka down")).when(kafkaTemplate).send(any(), any(), any());
        NotificationEvent event = NotificationEvent.builder().eventId("evt-1").recipientEmail("hr@test.com").build();

        notificationProducer.send(event);
    }
}
