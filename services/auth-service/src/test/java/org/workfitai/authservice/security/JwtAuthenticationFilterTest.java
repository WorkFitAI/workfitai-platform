package org.workfitai.authservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

/**
 * Unit tests for JwtAuthenticationFilter.
 *
 * JaCoCo excludes Filter classes so these tests verify correctness, not
 * coverage metrics.
 *
 * Uses MockHttpServletRequest/Response/FilterChain from spring-test;
 * Claims is mocked because jjwt-impl is runtime scope (not on compile classpath).
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock JwtService jwtService;

    @InjectMocks JwtAuthenticationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ─── shouldNotFilter ──────────────────────────────────────────────────────

    @Test
    void shouldNotFilter_optionsRequest_returnsTrue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/me");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_loginPath_returnsTrue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_actuatorHealthPath_returnsTrue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_protectedPath_returnsFalse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    // ─── doFilterInternal: no Authorization header ────────────────────────────

    @Test
    void doFilter_noAuthorizationHeader_chainCalledWithoutSettingAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // chain.doFilter was called
    }

    // ─── doFilterInternal: valid Bearer JWT ──────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void doFilter_validBearerToken_setsAuthenticationInSecurityContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        request.addHeader("Authorization", "Bearer valid.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.validateToken("valid.jwt.token")).thenReturn(true);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("alice");
        when(claims.getOrDefault(eq("roles"), any())).thenReturn(List.of("CANDIDATE"));
        when(claims.getOrDefault(eq("perms"), any())).thenReturn(List.of());
        when(jwtService.getClaims("valid.jwt.token")).thenReturn(claims);

        filter.doFilter(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getAuthorities()).hasSize(1);
    }

    // ─── doFilterInternal: case-insensitive "bearer " prefix ─────────────────

    @Test
    @SuppressWarnings("unchecked")
    void doFilter_bearerPrefixCaseInsensitive_extractsTokenCorrectly() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/profile");
        request.addHeader("Authorization", "BEARER valid.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.validateToken("valid.jwt.token")).thenReturn(true);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("bob");
        when(claims.getOrDefault(eq("roles"), any())).thenReturn(List.of("HR"));
        when(claims.getOrDefault(eq("perms"), any())).thenReturn(List.of());
        when(jwtService.getClaims("valid.jwt.token")).thenReturn(claims);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("bob");
    }

    // ─── doFilterInternal: invalid / expired JWT ─────────────────────────────

    @Test
    void doFilter_invalidJwt_clearsContextAndChainStillCalled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        request.addHeader("Authorization", "Bearer bad.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.validateToken("bad.jwt.token"))
                .thenThrow(new JwtException("JWT signature invalid"));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // chain.doFilter still called
    }

    @Test
    void doFilter_tokenValidFalse_contextRemainsEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        request.addHeader("Authorization", "Bearer expired.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.validateToken("expired.jwt.token")).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).getClaims(anyString());
    }
}
