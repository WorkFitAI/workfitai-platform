package org.workfitai.jobservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Jwt jwt(String sub, String companyId) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none").subject(sub);
        if (companyId != null) {
            builder.claim("companyId", companyId);
        }
        return builder.build();
    }

    @Test
    void getCurrentUserLogin_returnsEmpty_whenUnauthenticated() {
        assertThat(SecurityUtils.getCurrentUserLogin()).isEmpty();
    }

    @Test
    void getCurrentUserLogin_returnsUsername_whenAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, List.of()));

        assertThat(SecurityUtils.getCurrentUserLogin()).contains("alice");
    }

    @Test
    void getCurrentUserJWT_returnsEmpty_whenCredentialsNotString() {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt("alice", null), List.of()));

        assertThat(SecurityUtils.getCurrentUserJWT()).isEmpty();
    }

    @Test
    void getCurrentUserJWT_returnsToken_whenCredentialsIsString() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "raw-token", List.of()));

        assertThat(SecurityUtils.getCurrentUserJWT()).contains("raw-token");
    }

    @Test
    void isAuthenticated_returnsFalse_whenAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymous",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(SecurityUtils.isAuthenticated()).isFalse();
    }

    @Test
    void isAuthenticated_returnsTrue_whenAuthenticatedWithRealAuthority() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null,
                        List.of(new SimpleGrantedAuthority("ROLE_HR"))));

        assertThat(SecurityUtils.isAuthenticated()).isTrue();
    }

    @Test
    void isCurrentUserInRole_returnsTrue_whenAuthorityPresent() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null,
                        List.of(new SimpleGrantedAuthority("job:create"))));

        assertThat(SecurityUtils.isCurrentUserInRole("job:create")).isTrue();
    }

    @Test
    void isCurrentUserInRole_returnsFalse_whenUnauthenticated() {
        assertThat(SecurityUtils.isCurrentUserInRole("job:create")).isFalse();
    }

    @Test
    void getCurrentUser_returnsNull_whenUnauthenticated() {
        assertThat(SecurityUtils.getCurrentUser()).isNull();
    }

    @Test
    void getCurrentUser_returnsNull_whenPrincipalIsNotJwt() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, List.of()));

        assertThat(SecurityUtils.getCurrentUser()).isNull();
    }

    @Test
    void getCurrentUser_returnsSubjectClaim_whenPrincipalIsJwt() {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt("alice", null), List.of()));

        assertThat(SecurityUtils.getCurrentUser()).isEqualTo("alice");
    }

    @Test
    void getValidCompanyNo_returnsNull_whenUnauthenticated() {
        assertThat(SecurityUtils.getValidCompanyNo()).isNull();
    }

    @Test
    void getValidCompanyNo_returnsCompanyIdClaim_whenPrincipalIsJwt() {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt("hr1", "C-A"), List.of()));

        assertThat(SecurityUtils.getValidCompanyNo()).isEqualTo("C-A");
    }
}
