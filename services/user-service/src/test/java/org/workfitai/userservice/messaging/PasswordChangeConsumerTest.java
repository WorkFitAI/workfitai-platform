package org.workfitai.userservice.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.workfitai.userservice.dto.kafka.PasswordChangeEvent;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.model.CandidateEntity;
import org.workfitai.userservice.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordChangeConsumerTest {

    @Mock UserRepository userRepository;
    @Mock Acknowledgment ack;

    @InjectMocks
    PasswordChangeConsumer consumer;

    private static final String TOPIC = "password-change";
    private static final int PARTITION = 0;
    private static final long OFFSET = 1L;

    private UUID userId;
    private CandidateEntity user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = CandidateEntity.builder()
                .userId(userId)
                .email("u@test.com")
                .username("testuser")
                .passwordHash("$2a$10$oldhash")
                .userRole(EUserRole.CANDIDATE)
                .userStatus(EUserStatus.ACTIVE)
                .build();
    }

    // ---- null / invalid event ----

    @Test
    void handlePasswordChange_nullEvent_acksAndReturns() {
        consumer.handlePasswordChange(null, TOPIC, PARTITION, OFFSET, ack);

        verify(ack).acknowledge();
        verifyNoInteractions(userRepository);
    }

    @Test
    void handlePasswordChange_nullPasswordData_acksAndReturns() {
        PasswordChangeEvent event = PasswordChangeEvent.builder()
                .eventType("PASSWORD_CHANGED")
                .passwordData(null)
                .build();

        consumer.handlePasswordChange(event, TOPIC, PARTITION, OFFSET, ack);

        verify(ack).acknowledge();
        verifyNoInteractions(userRepository);
    }

    @Test
    void handlePasswordChange_wrongEventType_acksAndReturns() {
        PasswordChangeEvent event = buildEvent(userId.toString(), "UNKNOWN_TYPE");

        consumer.handlePasswordChange(event, TOPIC, PARTITION, OFFSET, ack);

        verify(ack).acknowledge();
        verifyNoInteractions(userRepository);
    }

    // ---- invalid userId ----

    @Test
    void handlePasswordChange_invalidUuidFormat_acksAndReturns() {
        PasswordChangeEvent event = buildEvent("not-a-uuid", "PASSWORD_CHANGED");

        consumer.handlePasswordChange(event, TOPIC, PARTITION, OFFSET, ack);

        verify(ack).acknowledge();
        verify(userRepository, never()).findById(any());
    }

    // ---- user not found ----

    @Test
    void handlePasswordChange_userNotFound_acksAndReturns() {
        PasswordChangeEvent event = buildEvent(userId.toString(), "PASSWORD_CHANGED");
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        consumer.handlePasswordChange(event, TOPIC, PARTITION, OFFSET, ack);

        verify(ack).acknowledge();
        verify(userRepository, never()).save(any());
    }

    // ---- success ----

    @Test
    void handlePasswordChange_success_updatesHashAndAcks() {
        PasswordChangeEvent event = buildEvent(userId.toString(), "PASSWORD_CHANGED");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        consumer.handlePasswordChange(event, TOPIC, PARTITION, OFFSET, ack);

        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$newhash");
        verify(userRepository).save(user);
        verify(ack).acknowledge();
    }

    // ---- exception propagates ----

    @Test
    void handlePasswordChange_repoThrows_rethrowsAndNoAck() {
        PasswordChangeEvent event = buildEvent(userId.toString(), "PASSWORD_CHANGED");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenThrow(new RuntimeException("db error"));

        assertThatThrownBy(() ->
                consumer.handlePasswordChange(event, TOPIC, PARTITION, OFFSET, ack))
                .isInstanceOf(RuntimeException.class);
        verify(ack, never()).acknowledge();
    }

    // ---- helper ----

    private PasswordChangeEvent buildEvent(String rawUserId, String eventType) {
        PasswordChangeEvent.PasswordData data = PasswordChangeEvent.PasswordData.builder()
                .userId(rawUserId)
                .username("testuser")
                .email("u@test.com")
                .newPasswordHash("$2a$10$newhash")
                .changeReason("USER_CHANGE")
                .build();

        return PasswordChangeEvent.builder()
                .eventId("evt-1")
                .eventType(eventType)
                .passwordData(data)
                .build();
    }
}
