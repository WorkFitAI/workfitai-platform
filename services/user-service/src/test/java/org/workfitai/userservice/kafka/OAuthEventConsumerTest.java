package org.workfitai.userservice.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.workfitai.userservice.dto.kafka.OAuthAccountLinkedEvent;
import org.workfitai.userservice.dto.kafka.OAuthAccountUnlinkedEvent;
import org.workfitai.userservice.exception.ApiException;
import org.workfitai.userservice.service.UserService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthEventConsumerTest {

    @Mock UserService userService;
    @Mock com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock Acknowledgment ack;

    @InjectMocks
    OAuthEventConsumer consumer;

    // ---- handleOAuthAccountLinked ----

    @Test
    void handleOAuthAccountLinked_success_addsProviderAndAcks() {
        OAuthAccountLinkedEvent event = OAuthAccountLinkedEvent.builder()
                .eventId("evt-1")
                .eventType("OAUTH_ACCOUNT_LINKED")
                .userId("auth-uid-1")
                .username("user1")
                .provider("GOOGLE")
                .providerEmail("g@test.com")
                .build();

        consumer.handleOAuthAccountLinked(event, ack);

        verify(userService).addOAuthProvider("user1", "GOOGLE", "g@test.com");
        verify(ack).acknowledge();
    }

    @Test
    void handleOAuthAccountLinked_serviceThrows_propagatesAndNoAck() {
        OAuthAccountLinkedEvent event = OAuthAccountLinkedEvent.builder()
                .eventId("evt-2")
                .username("user1")
                .provider("GOOGLE")
                .providerEmail("g@test.com")
                .build();

        doThrow(new ApiException("not found", org.springframework.http.HttpStatus.NOT_FOUND))
                .when(userService).addOAuthProvider(any(), any(), any());

        assertThatThrownBy(() -> consumer.handleOAuthAccountLinked(event, ack))
                .isInstanceOf(RuntimeException.class);
        verify(ack, never()).acknowledge();
    }

    // ---- handleOAuthAccountUnlinked ----

    @Test
    void handleOAuthAccountUnlinked_success_removesProviderAndAcks() {
        OAuthAccountUnlinkedEvent event = OAuthAccountUnlinkedEvent.builder()
                .eventId("evt-3")
                .eventType("OAUTH_ACCOUNT_UNLINKED")
                .username("user1")
                .provider("GOOGLE")
                .build();

        consumer.handleOAuthAccountUnlinked(event, ack);

        verify(userService).removeOAuthProvider("user1", "GOOGLE");
        verify(ack).acknowledge();
    }

    @Test
    void handleOAuthAccountUnlinked_serviceThrows_propagatesAndNoAck() {
        OAuthAccountUnlinkedEvent event = OAuthAccountUnlinkedEvent.builder()
                .eventId("evt-4")
                .username("user1")
                .provider("GOOGLE")
                .build();

        doThrow(new ApiException("not found", org.springframework.http.HttpStatus.NOT_FOUND))
                .when(userService).removeOAuthProvider(any(), any());

        assertThatThrownBy(() -> consumer.handleOAuthAccountUnlinked(event, ack))
                .isInstanceOf(RuntimeException.class);
        verify(ack, never()).acknowledge();
    }
}
