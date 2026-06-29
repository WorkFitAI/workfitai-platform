package org.workfitai.cvservice.security.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Jwt jwtWithClaims(Map<String, Object> claims, String subject) {
        return new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"), mergeSubject(claims, subject));
    }

    private Map<String, Object> mergeSubject(Map<String, Object> claims, String subject) {
        var merged = new java.util.HashMap<>(claims);
        merged.put("sub", subject);
        return merged;
    }

    @Test
    void getCurrentUserLogin_returnsEmpty_whenNoAuthentication() {
        assertThat(SecurityUtils.getCurrentUserLogin()).isEmpty();
    }

    @Test
    void getCurrentUserLogin_returnsEmpty_whenAuthenticationIsNotJwtToken() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("bob", null, List.of()));

        assertThat(SecurityUtils.getCurrentUserLogin()).isEmpty();
    }

    @Test
    void getCurrentUserLogin_prefersEmailClaim() {
        Jwt jwt = jwtWithClaims(Map.of("email", "alice@example.com", "username", "alice"), "sub-id");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(SecurityUtils.getCurrentUserLogin()).contains("alice@example.com");
    }

    @Test
    void getCurrentUserLogin_fallsBackToUsernameClaim_whenEmailAbsent() {
        Jwt jwt = jwtWithClaims(Map.of("username", "alice"), "sub-id");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(SecurityUtils.getCurrentUserLogin()).contains("alice");
    }

    @Test
    void getCurrentUserLogin_fallsBackToSubject_whenEmailAndUsernameAbsent() {
        Jwt jwt = jwtWithClaims(Map.of(), "sub-id");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(SecurityUtils.getCurrentUserLogin()).contains("sub-id");
    }

    @Test
    void getCurrentUserJWT_returnsEmpty_whenNoAuthentication() {
        assertThat(SecurityUtils.getCurrentUserJWT()).isEmpty();
    }

    @Test
    void getCurrentUserJWT_returnsTokenValue_whenJwtAuthenticationPresent() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getTokenValue()).thenReturn("raw-token");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(SecurityUtils.getCurrentUserJWT()).contains("raw-token");
    }
}
