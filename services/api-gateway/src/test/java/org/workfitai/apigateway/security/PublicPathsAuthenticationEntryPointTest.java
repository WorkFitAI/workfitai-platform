package org.workfitai.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

class PublicPathsAuthenticationEntryPointTest {

    private final PublicPathsAuthenticationEntryPoint entryPoint =
            new PublicPathsAuthenticationEntryPoint();

    @Test
    void commence_publicAuthPath_completesWithoutSettingUnauthorized() {
        ServerWebExchange exchange = exchangeFor("/auth/login");

        StepVerifier.create(entryPoint.commence(exchange, new BadCredentialsException("missing token")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void commence_publicOAuthPathPrefix_completesWithoutSettingUnauthorized() {
        ServerWebExchange exchange = exchangeFor("/auth/oauth/google/callback");

        StepVerifier.create(entryPoint.commence(exchange, new BadCredentialsException("missing token")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void commence_protectedPath_setsUnauthorized() {
        ServerWebExchange exchange = exchangeFor("/api/jobs");

        StepVerifier.create(entryPoint.commence(exchange, new BadCredentialsException("missing token")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ServerWebExchange exchangeFor(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path));
    }
}
