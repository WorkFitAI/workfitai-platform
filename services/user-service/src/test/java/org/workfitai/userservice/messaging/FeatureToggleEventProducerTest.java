package org.workfitai.userservice.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

@ExtendWith(MockitoExtension.class)
class FeatureToggleEventProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks FeatureToggleEventProducer producer;

    @BeforeEach
    void injectTopic() {
        ReflectionTestUtils.setField(producer, "featureToggleTopic", "platform-feature-toggle-events");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_success_sendsEventAndLogsOffset() {
        SendResult<String, Object> sendResult = mock(SendResult.class, RETURNS_DEEP_STUBS);
        when(sendResult.getRecordMetadata().offset()).thenReturn(0L);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        producer.publish("job-recommendation", true);

        verify(kafkaTemplate).send(
                eq("platform-feature-toggle-events"),
                eq("job-recommendation"),
                any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_kafkaFailure_logsError() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker error")));

        producer.publish("cv-referral", false);

        verify(kafkaTemplate).send(anyString(), eq("cv-referral"), any());
    }
}
