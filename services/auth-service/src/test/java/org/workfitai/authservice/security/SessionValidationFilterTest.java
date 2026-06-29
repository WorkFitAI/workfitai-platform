package org.workfitai.authservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.repository.UserSessionRepository;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Unit tests for SessionValidationFilter.
 * SecurityContextHolder state is set up per-test and torn down in @AfterEach.
 */
@ExtendWith(MockitoExtension.class)
class SessionValidationFilterTest {

    @Mock UserSessionRepository sessionRepository;
    @Mock UserRepository userRepository;

    @InjectMocks SessionValidationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(String username) {
        var auth = new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("CANDIDATE")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ─── shouldNotFilter ──────────────────────────────────────────────────────

    @Test
    void shouldNotFilter_loginPath_returnsTrue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_oauthCallbackPath_returnsTrue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth/callback/google");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_actuatorPath_returnsTrue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_protectedPath_returnsFalse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    // ─── doFilterInternal: no authentication ─────────────────────────────────

    @Test
    void doFilter_noAuthentication_chainCalledWithoutSessionCheck() throws Exception {
        // SecurityContext is empty — no auth set
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chain.getRequest()).isNotNull(); // chain was called
    }

    // ─── doFilterInternal: authenticated user with active sessions ────────────

    @Test
    void doFilter_authenticatedUserWithActiveSessions_chainCalled() throws Exception {
        setAuthenticatedUser("alice");
        User user = new User();
        user.setId("user-id-1");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(sessionRepository.countByUserId("user-id-1")).thenReturn(2L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chain.getRequest()).isNotNull();
    }

    // ─── doFilterInternal: user not found in DB ───────────────────────────────

    @Test
    void doFilter_authenticatedUser_notFoundInDb_returns401() throws Exception {
        setAuthenticatedUser("ghost");
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull(); // chain was NOT called (filter returned early)
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ─── doFilterInternal: no active sessions ────────────────────────────────

    @Test
    void doFilter_authenticatedUser_noActiveSessions_returns401() throws Exception {
        setAuthenticatedUser("alice");
        User user = new User();
        user.setId("user-id-1");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(sessionRepository.countByUserId("user-id-1")).thenReturn(0L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull(); // chain was NOT called
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
