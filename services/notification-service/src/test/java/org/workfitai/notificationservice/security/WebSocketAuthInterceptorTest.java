package org.workfitai.notificationservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtDecoder jwtDecoder;

    private WebSocketAuthInterceptor interceptor;

    private void init() {
        interceptor = new WebSocketAuthInterceptor(jwtDecoder);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private StompHeaderAccessor connectAccessor() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private Message<byte[]> toMessage(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private String longToken() {
        return "a".repeat(60);
    }

    private Jwt fakeJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("email", "alice@test.com")
                .claim("sub", "user-1")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void preSend_setsAuthentication_whenValidJwtProvided() {
        init();
        when(jwtDecoder.decode(longToken())).thenReturn(fakeJwt());
        StompHeaderAccessor accessor = connectAccessor();
        accessor.setNativeHeader("Authorization", "Bearer " + longToken());

        interceptor.preSend(toMessage(accessor), null);

        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getSessionAttributes()).containsEntry("userEmail", "alice@test.com");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void preSend_extractsTokenFromCustomTokenHeader() {
        init();
        when(jwtDecoder.decode(longToken())).thenReturn(fakeJwt());
        StompHeaderAccessor accessor = connectAccessor();
        accessor.setNativeHeader("token", longToken());

        interceptor.preSend(toMessage(accessor), null);

        assertThat(accessor.getUser()).isNotNull();
    }

    @Test
    void preSend_extractsTokenFromSessionAttribute() {
        init();
        when(jwtDecoder.decode(longToken())).thenReturn(fakeJwt());
        StompHeaderAccessor accessor = connectAccessor();
        accessor.getSessionAttributes().put("token", longToken());

        interceptor.preSend(toMessage(accessor), null);

        assertThat(accessor.getUser()).isNotNull();
    }

    @Test
    void preSend_fallsBackToUsername_whenJwtDecodingFails() {
        init();
        when(jwtDecoder.decode(longToken())).thenThrow(new RuntimeException("invalid token"));
        StompHeaderAccessor accessor = connectAccessor();
        accessor.setNativeHeader("Authorization", "Bearer " + longToken());
        accessor.setNativeHeader("X-Username", "bob");

        interceptor.preSend(toMessage(accessor), null);

        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("bob");
        assertThat(accessor.getSessionAttributes()).containsEntry("userEmail", "bob");
    }

    @Test
    void preSend_logsWarning_whenJwtDecodingFailsAndNoUsername() {
        init();
        when(jwtDecoder.decode(longToken())).thenThrow(new RuntimeException("invalid token"));
        StompHeaderAccessor accessor = connectAccessor();
        accessor.setNativeHeader("Authorization", "Bearer " + longToken());

        interceptor.preSend(toMessage(accessor), null);

        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void preSend_createsAnonymousAuth_whenOnlyUsernameHeaderProvided() {
        init();
        StompHeaderAccessor accessor = connectAccessor();
        accessor.setNativeHeader("X-Username", "carol");

        interceptor.preSend(toMessage(accessor), null);

        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("carol");
    }

    @Test
    void preSend_doesNothing_whenNoTokenAndNoUsername() {
        init();
        StompHeaderAccessor accessor = connectAccessor();

        interceptor.preSend(toMessage(accessor), null);

        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void preSend_treatsShortTokenAsOpaque_skipsJwtDecode() {
        init();
        StompHeaderAccessor accessor = connectAccessor();
        accessor.setNativeHeader("Authorization", "Bearer short");
        accessor.setNativeHeader("X-Username", "dave");

        interceptor.preSend(toMessage(accessor), null);

        assertThat(accessor.getUser().getName()).isEqualTo("dave");
    }

    @Test
    void preSend_ignoresNonConnectCommands() {
        init();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);

        Message<?> result = interceptor.preSend(toMessage(accessor), null);

        assertThat(result).isNotNull();
        assertThat(accessor.getUser()).isNull();
    }
}
