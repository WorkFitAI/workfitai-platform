package org.workfitai.userservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class SecurityContextUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- currentCallerCompanyNo ----

    @Test
    void companyNo_returnsNull_whenNoAuthentication() {
        SecurityContextHolder.clearContext();
        assertThat(SecurityContextUtils.currentCallerCompanyNo()).isNull();
    }

    @Test
    void companyNo_returnsNull_whenPrincipalIsNotJwt() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("string-principal");
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(SecurityContextUtils.currentCallerCompanyNo()).isNull();
    }

    @Test
    void companyNo_returnsCompanyId_whenJwtPrincipal() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("companyId")).thenReturn("TAX001");
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(SecurityContextUtils.currentCallerCompanyNo()).isEqualTo("TAX001");
    }

    @Test
    void companyNo_returnsNull_whenClaimIsAbsent() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("companyId")).thenReturn(null);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(SecurityContextUtils.currentCallerCompanyNo()).isNull();
    }

    // ---- callerHasRole ----

    @Test
    void hasRole_returnsFalse_whenNoAuthentication() {
        SecurityContextHolder.clearContext();
        assertThat(SecurityContextUtils.callerHasRole("ADMIN")).isFalse();
    }

    @Test
    void hasRole_returnsTrue_whenAuthorityMatches() {
        Authentication auth = mock(Authentication.class);
        List<GrantedAuthority> authorities = List.of(() -> "ROLE_ADMIN");
        when(auth.getAuthorities()).thenReturn((Collection) authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(SecurityContextUtils.callerHasRole("ADMIN")).isTrue();
    }

    @Test
    void hasRole_returnsFalse_whenAuthorityDoesNotMatch() {
        Authentication auth = mock(Authentication.class);
        List<GrantedAuthority> authorities = List.of(() -> "ROLE_CANDIDATE");
        when(auth.getAuthorities()).thenReturn((Collection) authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(SecurityContextUtils.callerHasRole("ADMIN")).isFalse();
    }
}
