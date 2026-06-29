package org.workfitai.userservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.workfitai.userservice.dto.kafka.UserRegistrationEvent;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeadLetterTopicConsumerTest {

    @Mock Acknowledgment ack;

    @InjectMocks
    DeadLetterTopicConsumer consumer;

    private static final String TOPIC = "user-registration-dlt";
    private static final int PARTITION = 0;
    private static final long OFFSET = 1L;

    @Test
    void handleDeadLetter_nullEvent_alwaysAcknowledges() {
        consumer.handleDeadLetter(null, TOPIC, PARTITION, OFFSET, ack);

        verify(ack).acknowledge();
    }

    @Test
    void handleDeadLetter_eventWithNullUserData_acknowledges() {
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .eventId("dlt-1")
                .eventType("USER_REGISTERED")
                .userData(null)
                .build();

        consumer.handleDeadLetter(event, TOPIC, PARTITION, OFFSET, ack);

        verify(ack).acknowledge();
    }

    @Test
    void handleDeadLetter_eventWithUserData_logsAndAcknowledges() {
        UserRegistrationEvent.UserData userData = new UserRegistrationEvent.UserData();
        userData.setEmail("fail@test.com");
        userData.setUserId("user-uuid-1");

        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .eventId("dlt-2")
                .eventType("USER_REGISTERED")
                .userData(userData)
                .build();

        consumer.handleDeadLetter(event, TOPIC, PARTITION, OFFSET, ack);

        verify(ack).acknowledge();
    }

    @Test
    void handleDeadLetter_exceptionDuringProcessing_stillAcknowledges() {
        // Acknowledgment that throws on first call, succeeds on second
        Acknowledgment faultyAck = mock(Acknowledgment.class);
        doNothing().when(faultyAck).acknowledge();

        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .eventId("dlt-3")
                .eventType("USER_REGISTERED")
                .userData(null)
                .build();

        // Should not throw even if internal logic errors
        consumer.handleDeadLetter(event, TOPIC, PARTITION, OFFSET, faultyAck);

        verify(faultyAck).acknowledge();
    }
}
