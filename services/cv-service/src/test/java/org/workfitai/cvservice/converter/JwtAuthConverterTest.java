package org.workfitai.cvservice.converter;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthConverterTest {

    private final JwtAuthConverter converter = new JwtAuthConverter();

    private Jwt jwtWithClaims(Map<String, Object> claims) {
        return new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"), claims);
    }

    @Test
    void convert_combinesRolesWithRolePrefixAndPermsWithoutPrefix() {
        Jwt jwt = jwtWithClaims(Map.of(
                "sub", "alice",
                "roles", List.of("CANDIDATE"),
                "perms", List.of("cv:read")));

        AbstractAuthenticationToken token = converter.convert(jwt);

        List<String> authorityNames = token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(authorityNames).containsExactlyInAnyOrder("ROLE_CANDIDATE", "cv:read");
    }

    @Test
    void convert_returnsEmptyAuthorities_whenNoRolesOrPermsClaims() {
        Jwt jwt = jwtWithClaims(Map.of("sub", "alice"));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void convert_unionsBothClaimsWithoutDuplicates() {
        Jwt jwt = jwtWithClaims(Map.of(
                "sub", "alice",
                "roles", List.of("ADMIN", "CANDIDATE"),
                "perms", List.of("cv:read", "cv:write")));

        AbstractAuthenticationToken token = converter.convert(jwt);

        List<String> authorityNames = token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(authorityNames).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_CANDIDATE", "cv:read", "cv:write");
    }
}
