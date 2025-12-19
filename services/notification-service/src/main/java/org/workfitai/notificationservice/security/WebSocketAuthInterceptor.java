package org.workfitai.notificationservice.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Intercepts WebSocket messages to authenticate users based on JWT tokens.
 * Extracts token from STOMP headers and validates it.
 */
@Component
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    public WebSocketAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Extract token from Authorization header or query parameter
            String token = extractToken(accessor);

            if (token != null) {
                try {
                    // Decode and validate JWT token
                    Jwt jwt = jwtDecoder.decode(token);

                    // Extract user information
                    String email = jwt.getClaimAsString("email");
                    String userId = jwt.getClaimAsString("sub");

                    // Extract roles from JWT
                    List<String> roles = jwt.getClaimAsStringList("roles");
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    if (roles != null) {
                        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(role)));
                    }

                    // Create authentication object
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(email, null, authorities);
                    authentication.setDetails(jwt);

                    // Set principal in STOMP header
                    accessor.setUser(authentication);

                    // Store user info in session attributes for later use
                    accessor.getSessionAttributes().put("userEmail", email);
                    accessor.getSessionAttributes().put("userId", userId);

                    // Set in SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.info("[WebSocket] User authenticated: email={}, userId={}", email, userId);

                } catch (Exception e) {
                    log.error("[WebSocket] Authentication failed: {}", e.getMessage());
                    // Let the connection proceed but without authentication
                    // You can throw exception here to reject connection if needed
                }
            } else {
                log.warn("[WebSocket] No authorization token provided");
            }
        }

        return message;
    }

    /**
     * Extract JWT token from STOMP headers or query parameters
     */
    private String extractToken(StompHeaderAccessor accessor) {
        // Try to get from Authorization header
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }

        // Try to get from token parameter (for SockJS which doesn't support custom
        // headers well)
        List<String> tokenHeaders = accessor.getNativeHeader("token");
        if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
            return tokenHeaders.get(0);
        }

        // Try to get from session attributes (set during handshake)
        Object sessionToken = accessor.getSessionAttributes().get("token");
        if (sessionToken != null) {
            return sessionToken.toString();
        }

        return null;
    }
}
