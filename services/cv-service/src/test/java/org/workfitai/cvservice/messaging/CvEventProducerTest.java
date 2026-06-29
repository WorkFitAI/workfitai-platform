package org.workfitai.cvservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.cvservice.dto.kafka.CvUpdatedEvent;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private CvEventProducer producer;

    private void init() {
        producer = new CvEventProducer(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "cvEventsTopic", "cv.updated");
    }

    private CvUpdatedEvent sampleEvent() {
        return CvUpdatedEvent.builder().eventId("e1").username("alice").cvId("cv-1").build();
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendCvUpdated_sendsToConfiguredTopicWithUsernameAsKey() {
        init();
        CvUpdatedEvent event = sampleEvent();
        SendResult<String, Object> sendResult = mock(SendResult.class, Answers.RETURNS_DEEP_STUBS);
        when(kafkaTemplate.send(eq("cv.updated"), eq("alice"), eq(event)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        producer.sendCvUpdated(event);

        verify(kafkaTemplate).send("cv.updated", "alice", event);
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendCvUpdated_logsError_whenFutureCompletesExceptionally() {
        init();
        CvUpdatedEvent event = sampleEvent();
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(eq("cv.updated"), eq("alice"), eq(event))).thenReturn(future);

        producer.sendCvUpdated(event);
    }

    @Test
    void sendCvUpdated_swallowsException_whenKafkaTemplateThrowsSynchronously() {
        init();
        CvUpdatedEvent event = sampleEvent();
        when(kafkaTemplate.send(eq("cv.updated"), eq("alice"), eq(event)))
                .thenThrow(new RuntimeException("boom"));

        producer.sendCvUpdated(event);
    }
}
