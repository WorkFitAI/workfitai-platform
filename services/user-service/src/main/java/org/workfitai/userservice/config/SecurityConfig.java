package org.workfitai.userservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.workfitai.userservice.security.PublicKeyProvider;
import org.workfitai.userservice.security.UserIdExtractionFilter;

import java.security.interfaces.RSAPublicKey;
import java.util.HashSet;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final PublicKeyProvider publicKeyProvider;
    private final UserIdExtractionFilter userIdExtractionFilter;

    @Value("${auth.jwt.issuer:auth-service}")
    private String expectedIssuer;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/api/v1/users/public/**", "/api/v1/internal/**", "/exists/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder()) // ✅ verify chữ ký bằng public key
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .addFilterAfter(userIdExtractionFilter, org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return new JwtDecoder() {
            private volatile NimbusJwtDecoder delegate;

            private NimbusJwtDecoder build() {
                try {
                    RSAPublicKey key = publicKeyProvider.getPublicKey();
                    NimbusJwtDecoder d = NimbusJwtDecoder.withPublicKey(key).build();
                    d.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
                    return d;
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to build JWT decoder", e);
                }
            }

            @Override
            public Jwt decode(String token) throws JwtException {
                if (delegate == null) {
                    synchronized (this) {
                        if (delegate == null) {
                            delegate = build();
                        }
                    }
                }
                try {
                    return delegate.decode(token);
                } catch (JwtException e) {
                    // Stale key after auth-service restart — refresh once and retry
                    synchronized (this) {
                        publicKeyProvider.invalidateKey();
                        delegate = build();
                    }
                    return delegate.decode(token);
                }
            }
        };
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        JwtGrantedAuthoritiesConverter permsConverter = new JwtGrantedAuthoritiesConverter();
        permsConverter.setAuthoritiesClaimName("perms");
        permsConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new HashSet<>(rolesConverter.convert(jwt));
            authorities.addAll(permsConverter.convert(jwt));
            return authorities;
        });

        // ✅ Extract username from "sub" claim as principal
        converter.setPrincipalClaimName("sub");

        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
