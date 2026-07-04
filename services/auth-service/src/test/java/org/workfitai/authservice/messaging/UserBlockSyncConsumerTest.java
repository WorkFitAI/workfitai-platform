package org.workfitai.authservice.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.authservice.dto.kafka.UserChangeEvent;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.repository.UserSessionRepository;
import org.workfitai.authservice.service.RefreshTokenService;

@ExtendWith(MockitoExtension.class)
class UserBlockSyncConsumerTest {

    @Mock UserRepository userRepository;
    @Mock UserSessionRepository sessionRepository;
    @Mock RefreshTokenService refreshTokenService;

    @InjectMocks UserBlockSyncConsumer consumer;

    private static final UUID USER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private UserChangeEvent event(String type, String username, boolean blocked) {
        return UserChangeEvent.builder()
                .eventType(type)
                .data(UserChangeEvent.UserEventData.builder()
                        .userId(USER_UUID)
                        .username(username)
                        .isBlocked(blocked)
                        .build())
                .build();
    }

    private User activeUser() {
        User u = new User();
        u.setId(USER_UUID.toString());
        u.setUsername("alice");
        u.setStatus(UserStatus.ACTIVE);
        u.setIsBlocked(false);
        return u;
    }

    // ─── null/irrelevant events ────────────────────────────────────────────────

    @Test
    void handleUserChangeEvent_nullEvent_doesNothing() {
        consumer.handleUserChangeEvent(null); // must not throw
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void handleUserChangeEvent_nullData_doesNothing() {
        UserChangeEvent evt = UserChangeEvent.builder().eventType("USER_BLOCKED").data(null).build();
        consumer.handleUserChangeEvent(evt);
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void handleUserChangeEvent_irrelevantEventType_ignoresEvent() {
        UserChangeEvent evt = event("USER_CREATED", "alice", false);
        consumer.handleUserChangeEvent(evt);
        verify(userRepository, never()).findByUsername(any());
    }

    // ─── USER_BLOCKED ─────────────────────────────────────────────────────────

    @Test
    void handleUserChangeEvent_BLOCKED_setsBlockedAndInvalidatesSessions() {
        User user = activeUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        consumer.handleUserChangeEvent(event("USER_BLOCKED", "alice", true));

        verify(userRepository).save(user);
        verify(sessionRepository).deleteByUserId(USER_UUID.toString());
        verify(refreshTokenService).deleteAllByUserId(USER_UUID.toString());
    }

    @Test
    void handleUserChangeEvent_BLOCKED_skipsIfAlreadyBlocked() {
        User user = activeUser();
        user.setIsBlocked(true); // already blocked
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        consumer.handleUserChangeEvent(event("USER_BLOCKED", "alice", true));

        verify(userRepository, never()).save(any());
        verify(sessionRepository, never()).deleteByUserId(any());
    }

    @Test
    void handleUserChangeEvent_BLOCKED_userNotFound_doesNothing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        consumer.handleUserChangeEvent(event("USER_BLOCKED", "ghost", true)); // must not throw
    }

    // ─── USER_UNBLOCKED ───────────────────────────────────────────────────────

    @Test
    void handleUserChangeEvent_UNBLOCKED_clearsBlockedFlag() {
        User user = activeUser();
        user.setIsBlocked(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        consumer.handleUserChangeEvent(event("USER_UNBLOCKED", "alice", false));

        verify(userRepository).save(user);
    }

    @Test
    void handleUserChangeEvent_UNBLOCKED_skipsIfAlreadyUnblocked() {
        User user = activeUser(); // isBlocked=false
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        consumer.handleUserChangeEvent(event("USER_UNBLOCKED", "alice", false));

        verify(userRepository, never()).save(any());
    }

    // ─── USER_DELETED ─────────────────────────────────────────────────────────

    @Test
    void handleUserChangeEvent_DELETED_invalidatesSessionsAndTokens() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser()));

        consumer.handleUserChangeEvent(event("USER_DELETED", "alice", false));

        verify(sessionRepository).deleteByUserId(USER_UUID.toString());
        verify(refreshTokenService).deleteAllByUserId(USER_UUID.toString());
        verify(userRepository, never()).save(any());
    }

    // ─── exception swallowing ─────────────────────────────────────────────────

    @Test
    void handleUserChangeEvent_repositoryThrows_doesNotPropagate() {
        when(userRepository.findByUsername("alice")).thenThrow(new RuntimeException("Mongo down"));

        consumer.handleUserChangeEvent(event("USER_BLOCKED", "alice", true)); // must not throw
    }
}
