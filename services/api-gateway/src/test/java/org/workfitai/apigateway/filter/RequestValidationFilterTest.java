package org.workfitai.apigateway.filter;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RequestValidationFilterTest {

    @Mock GatewayFilterChain chain;

    RequestValidationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestValidationFilter();
    }

    // ─── GET / OPTIONS bypass ─────────────────────────────────────────────────

    @Test
    void filter_GET_skipsValidationAndDelegatesToChain() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/users").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void filter_OPTIONS_skipsValidationAndDelegatesToChain() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("http://localhost/api/users").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    // ─── actuator / fallback bypass ───────────────────────────────────────────

    @Test
    void filter_actuatorPath_skipsAuthValidation() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://localhost/actuator/health")
                        .contentLength(100)
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void filter_fallbackPath_skipsAuthValidation() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://localhost/fallback")
                        .contentLength(50)
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    // ─── content-length limit ─────────────────────────────────────────────────

    @Test
    void filter_POST_withContentLengthExceedingLimit_returns413() {
        long oversized = 11 * 1024 * 1024L; // 11 MB > 10 MB limit
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://localhost/auth/login")
                        .contentLength(oversized)
                        .build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void filter_POST_withContentLengthWithinLimit_passesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://localhost/auth/login")
                        .contentLength(1024)
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    // ─── Authorization header check on protected paths ────────────────────────

    @Test
    void filter_POST_protectedPath_missingAuthHeader_returns401() {
        // Not /auth/, not /actuator, not /public/ → protected
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://localhost/api/jobs")
                        .contentLength(100)
                        .build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_POST_protectedPath_invalidAuthHeader_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://localhost/api/jobs")
                        .contentLength(100)
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_POST_protectedPath_validBearerToken_delegatesToChain() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://localhost/api/jobs")
                        .contentLength(100)
                        .header("Authorization", "Bearer valid-jwt-token")
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void filter_POST_authPath_noAuthHeaderNeeded() {
        // /auth/ prefix is a public path — no Authorization required
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://localhost/auth/login")
                        .contentLength(200)
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void filter_POST_publicSubpath_noAuthHeaderNeeded() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://localhost/api/jobs/public/list")
                        .contentLength(50)
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    // ─── filter order ─────────────────────────────────────────────────────────

    @Test
    void getOrder_returnsNegative10() {
        assertThat(filter.getOrder()).isEqualTo(-10);
    }
}
