package org.workfitai.authservice.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.authservice.dto.kafka.SessionInvalidationEvent;
import org.workfitai.authservice.repository.UserSessionRepository;

@ExtendWith(MockitoExtension.class)
class SessionInvalidationConsumerTest {

    @Mock UserSessionRepository sessionRepository;

    @InjectMocks SessionInvalidationConsumer consumer;

    private SessionInvalidationEvent event(String userId, String username, String reason) {
        return SessionInvalidationEvent.builder()
                .userId(java.util.UUID.fromString(userId))
                .username(username)
                .reason(reason)
                .build();
    }

    @Test
    void handleSessionInvalidation_deletesAllSessionsForUser() {
        SessionInvalidationEvent evt = event(
                "00000000-0000-0000-0000-000000000001", "alice", "USER_BLOCKED");
        when(sessionRepository.deleteByUserId("00000000-0000-0000-0000-000000000001")).thenReturn(3);

        consumer.handleSessionInvalidation(evt);

        verify(sessionRepository).deleteByUserId("00000000-0000-0000-0000-000000000001");
    }

    @Test
    void handleSessionInvalidation_doesNotThrow_whenNoSessionsExist() {
        SessionInvalidationEvent evt = event(
                "00000000-0000-0000-0000-000000000002", "bob", "ACCOUNT_DELETED");
        when(sessionRepository.deleteByUserId("00000000-0000-0000-0000-000000000002")).thenReturn(0);

        consumer.handleSessionInvalidation(evt); // must not throw
    }

    @Test
    void handleSessionInvalidation_doesNotThrow_whenRepositoryThrows() {
        SessionInvalidationEvent evt = event(
                "00000000-0000-0000-0000-000000000003", "carol", "USER_BLOCKED");
        when(sessionRepository.deleteByUserId("00000000-0000-0000-0000-000000000003"))
                .thenThrow(new RuntimeException("MongoDB unavailable"));

        // Consumer must swallow exceptions and allow Kafka to retry
        consumer.handleSessionInvalidation(evt); // must not throw
    }
}
