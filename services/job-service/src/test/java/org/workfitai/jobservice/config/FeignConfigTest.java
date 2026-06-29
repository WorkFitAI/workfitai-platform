package org.workfitai.jobservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeignConfigTest {

    private final FeignConfig feignConfig = new FeignConfig();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestInterceptor_addsAuthorizationHeader_whenJwtAuthenticationPresent() {
        Jwt jwt = Jwt.withTokenValue("token-value").header("alg", "none").subject("hr1").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        RequestInterceptor interceptor = feignConfig.requestInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer token-value");
    }

    @Test
    void requestInterceptor_addsNoHeader_whenNoJwtAuthenticationPresent() {
        RequestInterceptor interceptor = feignConfig.requestInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("Authorization")).isNull();
    }
}
