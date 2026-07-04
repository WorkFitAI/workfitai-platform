package org.workfitai.authservice.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.authservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserStatusSyncConsumerTest {

    @Mock UserRepository userRepository;

    @InjectMocks UserStatusSyncConsumer consumer;

    private UserRegistrationEvent event(String type, String email, String status) {
        return UserRegistrationEvent.builder()
                .eventType(type)
                .userData(UserRegistrationEvent.UserData.builder()
                        .email(email)
                        .status(status)
                        .build())
                .build();
    }

    private User waitApprovedUser() {
        User u = new User();
        u.setId("user-id-1");
        u.setEmail("alice@example.com");
        u.setStatus(UserStatus.WAIT_APPROVED);
        return u;
    }

    // ─── null / irrelevant events ──────────────────────────────────────────────

    @Test
    void handleUserStatusUpdate_nullEvent_doesNothing() {
        consumer.handleUserStatusUpdate(null); // must not throw
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void handleUserStatusUpdate_nullUserData_doesNothing() {
        UserRegistrationEvent evt = UserRegistrationEvent.builder()
                .eventType("HR_MANAGER_APPROVED").userData(null).build();
        consumer.handleUserStatusUpdate(evt);
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void handleUserStatusUpdate_irrelevantEventType_isIgnored() {
        consumer.handleUserStatusUpdate(event("USER_REGISTERED", "alice@example.com", "ACTIVE"));
        verify(userRepository, never()).findByEmail(any());
    }

    // ─── HR_MANAGER_APPROVED ──────────────────────────────────────────────────

    @Test
    void handleUserStatusUpdate_HR_MANAGER_APPROVED_syncsStatusToActive() {
        User user = waitApprovedUser();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        consumer.handleUserStatusUpdate(event("HR_MANAGER_APPROVED", "alice@example.com", "ACTIVE"));

        verify(userRepository).save(user);
    }

    @Test
    void handleUserStatusUpdate_HR_APPROVED_syncsStatusToActive() {
        User user = waitApprovedUser();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        consumer.handleUserStatusUpdate(event("HR_APPROVED", "alice@example.com", "ACTIVE"));

        verify(userRepository).save(user);
    }

    @Test
    void handleUserStatusUpdate_skipsIfStatusAlreadyMatches() {
        User user = waitApprovedUser();
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        consumer.handleUserStatusUpdate(event("HR_MANAGER_APPROVED", "alice@example.com", "ACTIVE"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void handleUserStatusUpdate_userNotFound_doesNothing() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        consumer.handleUserStatusUpdate(event("HR_MANAGER_APPROVED", "ghost@example.com", "ACTIVE"));

        verify(userRepository, never()).save(any());
    }

    // ─── exception swallowing ─────────────────────────────────────────────────

    @Test
    void handleUserStatusUpdate_repositoryThrows_doesNotPropagate() {
        when(userRepository.findByEmail("alice@example.com"))
                .thenThrow(new RuntimeException("Mongo unavailable"));

        consumer.handleUserStatusUpdate(event("HR_MANAGER_APPROVED", "alice@example.com", "ACTIVE"));
        // must not throw
    }
}
