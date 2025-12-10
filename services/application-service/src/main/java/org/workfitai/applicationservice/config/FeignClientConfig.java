package org.workfitai.applicationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign client configuration to propagate JWT tokens to downstream services.
 * 
 * This interceptor automatically adds the Authorization header with the JWT
 * token
 * from the current security context to all outgoing Feign requests.
 * 
 * Use case:
 * When application-service calls user-service or other services via Feign,
 * the JWT token from the original request needs to be forwarded so the
 * downstream service can authenticate and authorize the request.
 */
@Configuration
@Slf4j
public class FeignClientConfig {

    /**
     * Request interceptor that forwards JWT token from SecurityContext
     * to downstream services.
     * 
     * Extracts the JWT token from the current authentication and adds it
     * to the Authorization header of outgoing Feign requests.
     * 
     * @return RequestInterceptor bean
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                    String tokenValue = jwtAuth.getToken().getTokenValue();
                    template.header("Authorization", "Bearer " + tokenValue);
                    log.debug("Added Authorization header to Feign request: {}", template.url());
                } else {
                    log.warn("No JWT token found in SecurityContext for Feign request to: {}", template.url());
                }
            }
        };
    }
}
