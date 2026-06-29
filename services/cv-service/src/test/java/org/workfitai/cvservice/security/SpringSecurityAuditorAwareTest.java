package org.workfitai.cvservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.workfitai.cvservice.constant.CVConst;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityAuditorAwareTest {

    private final SpringSecurityAuditorAware auditorAware = new SpringSecurityAuditorAware();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentAuditor_returnsUsername_whenAuthenticated() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("username", "alice")
                .subject("alice")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertThat(auditorAware.getCurrentAuditor()).contains("alice");
    }

    @Test
    void getCurrentAuditor_fallsBackToSystemAccount_whenNotAuthenticated() {
        assertThat(auditorAware.getCurrentAuditor()).contains(CVConst.SYSTEM_ACCOUNT);
    }
}
