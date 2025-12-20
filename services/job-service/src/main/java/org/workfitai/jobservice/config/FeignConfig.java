package org.workfitai.jobservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Slf4j
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                if (authentication instanceof JwtAuthenticationToken) {
                    JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
                    String token = jwtAuth.getToken().getTokenValue();

                    // Add Bearer token to Authorization header
                    template.header("Authorization", "Bearer " + token);
                    log.debug("Added Authorization header to Feign request: {}", template.url());
                } else {
                    log.warn("No JWT authentication found in SecurityContext for Feign request to: {}", template.url());
                }
            }
        };
    }
}
