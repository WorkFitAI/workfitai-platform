package org.workfitai.userservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.workfitai.userservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.service.CandidateService;
import org.workfitai.userservice.service.HRService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationConsumerTest {

    @Mock CandidateService candidateService;
    @Mock HRService hrService;
    @Mock Acknowledgment ack;

    @InjectMocks
    UserRegistrationConsumer consumer;

    private static final String TOPIC = "user-registration";
    private static final int PARTITION = 0;
    private static final long OFFSET = 1L;

    // ---- null / invalid event ----

    @Test
    void handleUserRegistration_nullEvent_acksAndReturns() {
        consumer.handleUserRegistration(null, TOPIC, PARTITION, OFFSET, ack);

        verify(ack).acknowledge();
        verifyNoInteractions(candidateService, hrService);
    }

    @Test
    void handleUserRegistration_nullUserData_acksAndReturns() {
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .eventType("USER_REGISTERED")
                .userData(null)
                .build();

        consumer.handleUserRegistration(event, TOPIC, PARTITION, OFFSET, ack);

        verify(ack).acknowledge();
        verifyNoInteractions(candidateService, hrService);
    }

    @Test
    void handleUserRegistration_unknownEventType_acksAndReturns() {
        UserRegistrationEvent event = buildEvent("UNKNOWN_TYPE", "CANDIDATE");

        consumer.handleUserRegistration(event, TOPIC, PARTITION, OFFSET, ack);

        verify(ack).acknowledge();
        verifyNoInteractions(candidateService, hrService);
    }

    // ---- USER_REGISTERED routing ----

    @Test
    void handleUserRegistration_candidateRole_routesToCandidateService() {
        UserRegistrationEvent event = buildEvent("USER_REGISTERED", "CANDIDATE");

        consumer.handleUserRegistration(event, TOPIC, PARTITION, OFFSET, ack);

        verify(candidateService).createFromKafkaEvent(event.getUserData());
        verify(ack).acknowledge();
    }

    @Test
    void handleUserRegistration_hrRole_routesToHrService() {
        UserRegistrationEvent event = buildEvent("USER_REGISTERED", "HR");

        consumer.handleUserRegistration(event, TOPIC, PARTITION, OFFSET, ack);

        verify(hrService).createFromKafkaEvent(event.getUserData());
        verify(ack).acknowledge();
    }

    @Test
    void handleUserRegistration_hrManagerRole_routesToHrService() {
        UserRegistrationEvent event = buildEvent("USER_REGISTERED", "HR_MANAGER");

        consumer.handleUserRegistration(event, TOPIC, PARTITION, OFFSET, ack);

        verify(hrService).createFromKafkaEvent(event.getUserData());
        verify(ack).acknowledge();
    }

    @Test
    void handleUserRegistration_nullRole_acksAndReturns() {
        UserRegistrationEvent.UserData userData = buildUserData(null);
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .eventType("USER_REGISTERED")
                .userData(userData)
                .build();

        consumer.handleUserRegistration(event, TOPIC, PARTITION, OFFSET, ack);

        verify(ack).acknowledge();
        verifyNoInteractions(candidateService, hrService);
    }

    // ---- HR_MANAGER_APPROVED / HR_APPROVED ----

    @Test
    void handleUserRegistration_hrManagerApproved_updatesStatus() {
        UserRegistrationEvent.UserData userData = buildUserData("HR_MANAGER");
        userData.setStatus("ACTIVE");
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .eventType("HR_MANAGER_APPROVED")
                .userData(userData)
                .build();

        consumer.handleUserRegistration(event, TOPIC, PARTITION, OFFSET, ack);

        verify(hrService).updateStatus(userData.getEmail(), EUserStatus.ACTIVE);
        verify(ack).acknowledge();
    }

    @Test
    void handleUserRegistration_hrApproved_updatesCandidateStatus() {
        UserRegistrationEvent.UserData userData = buildUserData("CANDIDATE");
        userData.setStatus("ACTIVE");
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .eventType("HR_APPROVED")
                .userData(userData)
                .build();

        consumer.handleUserRegistration(event, TOPIC, PARTITION, OFFSET, ack);

        verify(candidateService).updateStatus(userData.getEmail(), EUserStatus.ACTIVE);
        verify(ack).acknowledge();
    }

    // ---- exception ----

    @Test
    void handleUserRegistration_serviceThrows_propagatesException() {
        UserRegistrationEvent event = buildEvent("USER_REGISTERED", "CANDIDATE");
        doThrow(new RuntimeException("db error")).when(candidateService).createFromKafkaEvent(any());

        assertThatThrownBy(() ->
                consumer.handleUserRegistration(event, TOPIC, PARTITION, OFFSET, ack))
                .isInstanceOf(RuntimeException.class);
        verify(ack, never()).acknowledge();
    }

    // ---- helpers ----

    private UserRegistrationEvent buildEvent(String eventType, String role) {
        return UserRegistrationEvent.builder()
                .eventId("evt-1")
                .eventType(eventType)
                .userData(buildUserData(role))
                .build();
    }

    private UserRegistrationEvent.UserData buildUserData(String role) {
        UserRegistrationEvent.UserData d = new UserRegistrationEvent.UserData();
        d.setEmail("user@test.com");
        d.setUsername("testuser");
        d.setFullName("Test User");
        d.setPhoneNumber("0900000001");
        d.setPasswordHash("$2a$10$hashed");
        d.setRole(role);
        d.setStatus("PENDING");
        return d;
    }
}
