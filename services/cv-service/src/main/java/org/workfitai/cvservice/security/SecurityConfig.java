package org.workfitai.cvservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.workfitai.cvservice.converter.JwtAuthConverter;

@Configuration
public class SecurityConfig {

    private final SecurityJwtConfiguration jwtConfig;
    private final JwtAuthConverter jwtAuthConverter;

    public SecurityConfig(JwtAuthConverter jwtAuthConverter, SecurityJwtConfiguration jwtConfig) {
        this.jwtConfig = jwtConfig;
        this.jwtAuthConverter = jwtAuthConverter;

    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/refresh",
                                "/actuator/**", "/error",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/public/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/hr/**").hasRole("HR")
                        .requestMatchers("/candidate/**").hasRole("CANDIDATE")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {
                                    try {
                                        jwt
                                                .decoder(jwtConfig.jwtDecoder())
                                                .jwtAuthenticationConverter(jwtAuthConverter);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                        ));

        return http.build();
    }

}
