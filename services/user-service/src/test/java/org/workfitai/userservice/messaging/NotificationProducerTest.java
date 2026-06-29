package org.workfitai.userservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.userservice.dto.kafka.NotificationEvent;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    NotificationProducer producer;

    @Test
    void send_nullSourceService_setsUserServiceAndSendsToTopic() {
        ReflectionTestUtils.setField(producer, "notificationTopic", "notification-events");
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        NotificationEvent event = NotificationEvent.builder()
                .eventId("evt-1")
                .eventType("EMAIL_NOTIFICATION")
                .recipientEmail("u@test.com")
                .build();

        producer.send(event);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("notification-events"), eq("u@test.com"), payloadCaptor.capture());

        NotificationEvent sent = (NotificationEvent) payloadCaptor.getValue();
        assertThat(sent.getSourceService()).isEqualTo("user-service");
    }

    @Test
    void send_sourceServiceAlreadySet_doesNotOverwrite() {
        ReflectionTestUtils.setField(producer, "notificationTopic", "notification-events");
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        NotificationEvent event = NotificationEvent.builder()
                .eventId("evt-2")
                .recipientEmail("u@test.com")
                .sourceService("other-service")
                .build();

        producer.send(event);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("notification-events"), eq("u@test.com"), captor.capture());
        assertThat(((NotificationEvent) captor.getValue()).getSourceService()).isEqualTo("other-service");
    }
}
