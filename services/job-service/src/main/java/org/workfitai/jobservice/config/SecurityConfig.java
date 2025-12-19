package org.workfitai.jobservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.workfitai.jobservice.security.SecurityJwtConfiguration;

import java.util.HashSet;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityJwtConfiguration jwtConfig;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/api/v1/jobs/public/**", "/public/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/hr/**").hasRole("HR")
                        .requestMatchers("/candidate/**").hasRole("CANDIDATE")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            res.getWriter().write("""
                                        {
                                          "status": 401,
                                          "message": "Unauthorized",
                                          "source": "job"
                                        }
                                    """);
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(403);
                            res.setContentType("application/json");
                            res.getWriter().write("""
                                        {
                                          "status": 403,
                                          "message": "Access Denied",
                                          "source": "job"
                                        }
                                    """);
                        })
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {
                                    try {
                                        jwt
                                                .decoder(jwtConfig.jwtDecoder())
                                                .jwtAuthenticationConverter(jwtAuthenticationConverter());
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                        ));
        return http.build();
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
}
