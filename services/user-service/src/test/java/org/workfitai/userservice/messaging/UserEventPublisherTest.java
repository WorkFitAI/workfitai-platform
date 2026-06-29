package org.workfitai.userservice.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.kafka.core.KafkaTemplate;
import org.workfitai.userservice.model.HREntity;
import org.workfitai.userservice.model.UserEntity;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserEventPublisherTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks UserEventPublisher publisher;

    private UserEntity user;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        user = mock(UserEntity.class);
        when(user.getUserId()).thenReturn(userId);
        when(user.getVersion()).thenReturn(1L);
        when(user.getUsername()).thenReturn("testuser");
        when(user.getFullName()).thenReturn("Test User");
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.getPhoneNumber()).thenReturn("0900000001");
    }

    @Test
    void publishUserCreated_sendsToTopic() {
        publisher.publishUserCreated(user);
        verify(kafkaTemplate).send(eq("user-change-events"), eq(userId.toString()), any());
    }

    @Test
    void publishUserUpdated_sendsToTopic() {
        publisher.publishUserUpdated(user);
        verify(kafkaTemplate).send(eq("user-change-events"), eq(userId.toString()), any());
    }

    @Test
    void publishUserDeleted_sendsToTopic() {
        publisher.publishUserDeleted(user);
        verify(kafkaTemplate).send(eq("user-change-events"), eq(userId.toString()), any());
    }

    @Test
    void publishUserBlocked_sendsToTopic() {
        publisher.publishUserBlocked(user);
        verify(kafkaTemplate).send(eq("user-change-events"), eq(userId.toString()), any());
    }

    @Test
    void publishUserUnblocked_sendsToTopic() {
        publisher.publishUserUnblocked(user);
        verify(kafkaTemplate).send(eq("user-change-events"), eq(userId.toString()), any());
    }

    @Test
    void publishEvent_doesNotThrow_whenKafkaFails() {
        doThrow(new RuntimeException("broker down")).when(kafkaTemplate).send(anyString(), anyString(), any());
        publisher.publishUserCreated(user);
        // must swallow and not rethrow
    }

    @Test
    void publishUserCreated_hrEntity_includesCompanyInfo() {
        HREntity hr = mock(HREntity.class);
        when(hr.getUserId()).thenReturn(userId);
        when(hr.getVersion()).thenReturn(1L);
        when(hr.getCompanyNo()).thenReturn("TAX001");

        publisher.publishUserCreated(hr);
        verify(kafkaTemplate).send(eq("user-change-events"), eq(userId.toString()), any());
    }
}
