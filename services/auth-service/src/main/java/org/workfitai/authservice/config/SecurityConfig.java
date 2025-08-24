package org.workfitai.authservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.response.ApiError;
import org.workfitai.authservice.response.ResponseData;
import org.workfitai.authservice.security.JwtAuthenticationFilter;
import org.workfitai.authservice.web.MdcTraceFilter;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter, ObjectMapper om) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/register", "/login", "/refresh",
                                "/actuator/**", "/error",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, ex1) -> {
                            res.setStatus(401);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            var err = ApiError.builder()
                                    .code(401).message("Unauthorized")
                                    .traceId(MDC.get(MdcTraceFilter.TRACE_ID))
                                    .timestamp(OffsetDateTime.now())
                                    .build();
                            res.getOutputStream().write(om.writeValueAsBytes(err));
                        })
                        .accessDeniedHandler((req, res, ex2) -> {
                            res.setStatus(403);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            var err = ApiError.builder()
                                    .code(403).message("Forbidden")
                                    .traceId(MDC.get(MdcTraceFilter.TRACE_ID))
                                    .timestamp(OffsetDateTime.now())
                                    .build();
                            res.getOutputStream().write(om.writeValueAsBytes(err));
                        })
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return om;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByUsername(username)
                .or(() -> users.findByEmail(username))
                .map(u -> new org.springframework.security.core.userdetails.User(
                        u.getUsername(),
                        u.getPassword(),
                        u.getRoles().stream()
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toList())
                ))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}