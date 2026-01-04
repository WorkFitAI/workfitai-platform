package org.workfitai.cvservice.converter;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.HashSet;

@Configuration
public class JwtAuthConverter extends JwtAuthenticationConverter {

    public JwtAuthConverter() {
        this.setJwtGrantedAuthoritiesConverter(new CombinedAuthoritiesConverter());
    }

    private static class CombinedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

        private final JwtGrantedAuthoritiesConverter rolesConverter;
        private final JwtGrantedAuthoritiesConverter permsConverter;

        public CombinedAuthoritiesConverter() {
            // Converter cho roles
            rolesConverter = new JwtGrantedAuthoritiesConverter();
            rolesConverter.setAuthoritiesClaimName("roles");
            rolesConverter.setAuthorityPrefix("ROLE_");

            // Converter cho perms
            permsConverter = new JwtGrantedAuthoritiesConverter();
            permsConverter.setAuthoritiesClaimName("perms");
            permsConverter.setAuthorityPrefix("");
        }

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            var authorities = new HashSet<>(rolesConverter.convert(jwt));
            authorities.addAll(permsConverter.convert(jwt));
            return authorities;
        }
    }
}
