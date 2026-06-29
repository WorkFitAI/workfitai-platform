package org.workfitai.cvservice.controller;

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
import org.workfitai.cvservice.dto.kafka.NotificationEvent;
import org.workfitai.cvservice.messaging.NotificationProducer;

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

    @Test
    void testNotification_usesUsernameAsEmail_whenUsernameContainsAtSign() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@test.com", null));

        ResponseEntity<?> response = controller.testNotification(null);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationProducer).send(captor.capture());
        assertThat(captor.getValue().getRecipientEmail()).isEqualTo("alice@test.com");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(((Map<?, ?>) response.getBody()).get("username")).isEqualTo("alice@test.com");
    }

    @Test
    void testNotification_appendsTestDomain_whenUsernameHasNoAtSign() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null));

        controller.testNotification(null);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationProducer).send(captor.capture());
        assertThat(captor.getValue().getRecipientEmail()).isEqualTo("alice@test.com");
    }
}
