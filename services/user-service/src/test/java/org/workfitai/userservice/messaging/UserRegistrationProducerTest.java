package org.workfitai.userservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.userservice.dto.kafka.UserRegistrationEvent;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    UserRegistrationProducer producer;

    @Test
    void publishUserRegistrationEvent_withUserData_usesEmailAsKey() {
        ReflectionTestUtils.setField(producer, "userRegistrationTopic", "user-registration");
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        UserRegistrationEvent.UserData userData = new UserRegistrationEvent.UserData();
        userData.setEmail("hr@company.com");

        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .eventId("evt-1")
                .eventType("HR_MANAGER_APPROVED")
                .userData(userData)
                .build();

        producer.publishUserRegistrationEvent(event);

        verify(kafkaTemplate).send(eq("user-registration"), eq("hr@company.com"), eq(event));
    }

    @Test
    void publishUserRegistrationEvent_withoutUserData_usesEventIdAsKey() {
        ReflectionTestUtils.setField(producer, "userRegistrationTopic", "user-registration");
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .eventId("evt-2")
                .eventType("HR_APPROVED")
                .userData(null)
                .build();

        producer.publishUserRegistrationEvent(event);

        verify(kafkaTemplate).send(eq("user-registration"), eq("evt-2"), eq(event));
    }
}
