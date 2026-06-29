package org.workfitai.jobservice.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.workfitai.jobservice.dto.kafka.NotificationEvent;
import org.workfitai.jobservice.messaging.NotificationProducer;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TestNotificationControllerTest {

    @Mock
    private NotificationProducer notificationProducer;
    @InjectMocks
    private TestNotificationController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList()));
    }

    @Test
    void testNotification_usesUsernameAsEmail_whenUsernameAlreadyLooksLikeEmail() {
        authenticateAs("alice@test.com");

        ResponseEntity<?> response = controller.testNotification(SecurityContextHolder.getContext().getAuthentication());

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationProducer).send(captor.capture());
        assertThat(captor.getValue().getRecipientEmail()).isEqualTo("alice@test.com");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("username", "alice@test.com");
    }

    @Test
    void testNotification_appendsTestDomain_whenUsernameHasNoAtSign() {
        authenticateAs("alice");

        controller.testNotification(SecurityContextHolder.getContext().getAuthentication());

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationProducer).send(captor.capture());
        assertThat(captor.getValue().getRecipientEmail()).isEqualTo("alice@test.com");
        assertThat(captor.getValue().getEventType()).isEqualTo("JOB_CREATED");
    }
}
