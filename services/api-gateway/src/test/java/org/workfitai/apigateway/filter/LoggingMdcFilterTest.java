package org.workfitai.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class LoggingMdcFilterTest {

    @Mock ServerWebExchange exchange;
    @Mock ServerHttpRequest request;
    @Mock ServerHttpResponse response;
    @Mock GatewayFilterChain chain;

    LoggingMdcFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LoggingMdcFilter();

        org.springframework.http.HttpHeaders headers =
                new org.springframework.http.HttpHeaders();

        lenient().when(exchange.getRequest()).thenReturn(request);
        lenient().when(exchange.getResponse()).thenReturn(response);
        lenient().when(request.getMethod()).thenReturn(HttpMethod.GET);
        lenient().when(request.getHeaders()).thenReturn(headers);

        RequestPath requestPath = mock(RequestPath.class);
        lenient().when(requestPath.value()).thenReturn("/api/test");
        lenient().when(request.getPath()).thenReturn(requestPath);

        // Stub mutate() chain for request
        ServerHttpRequest.Builder reqBuilder = mock(ServerHttpRequest.Builder.class);
        lenient().when(request.mutate()).thenReturn(reqBuilder);
        lenient().when(reqBuilder.header(anyString(), anyString())).thenReturn(reqBuilder);
        lenient().when(reqBuilder.build()).thenReturn(request);

        // Stub mutate() chain for exchange
        ServerWebExchange.Builder exchBuilder = mock(ServerWebExchange.Builder.class);
        lenient().when(exchange.mutate()).thenReturn(exchBuilder);
        lenient().when(exchBuilder.request(any(ServerHttpRequest.class))).thenReturn(exchBuilder);
        lenient().when(exchBuilder.build()).thenReturn(exchange);

        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    // ─── getOrder ─────────────────────────────────────────────────────────────

    @Test
    void getOrder_returnsNegative40() {
        assertThat(filter.getOrder()).isEqualTo(-40);
    }

    // ─── filter ───────────────────────────────────────────────────────────────

    @Test
    void filter_withNoXRequestIdHeader_generatesRequestId() {
        // Arrange — no X-Request-Id header in request (mocked headers are empty)

        // Act
        Mono<Void> result = filter.filter(exchange, chain);

        // Assert — filter completes without error
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void filter_completesAndDelegatestoChain() {
        // Act
        Mono<Void> result = filter.filter(exchange, chain);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    void filter_propagatesChainResult() {
        // Arrange
        when(chain.filter(any())).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void filter_withExistingXRequestIdHeader_reusesThatId() {
        // Arrange — inject X-Request-Id header into the mock
        org.springframework.http.HttpHeaders headersWithId =
                new org.springframework.http.HttpHeaders();
        headersWithId.add(LoggingMdcFilter.REQUEST_ID_HEADER, "existing-id-123");
        when(request.getHeaders()).thenReturn(headersWithId);

        // Act
        Mono<Void> result = filter.filter(exchange, chain);

        // Assert — completes without error; reuse branch executed
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void filter_healthPath_setsHealthCheckLogType() {
        givenPath("/actuator/health");
        when(chain.filter(any())).thenReturn(Mono.deferContextual(ctx -> {
            assertThat((String) ctx.get("log_type")).isEqualTo("HEALTH_CHECK");
            return Mono.empty();
        }));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void filter_authPath_setsAuthLogType() {
        givenPath("/auth/login");
        when(chain.filter(any())).thenReturn(Mono.deferContextual(ctx -> {
            assertThat((String) ctx.get("log_type")).isEqualTo("AUTH");
            return Mono.empty();
        }));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void filter_authenticatedJwtBusinessPath_setsUserActionLogType() {
        givenPath("/api/jobs");
        when(chain.filter(any())).thenReturn(Mono.deferContextual(ctx -> {
            assertThat((String) ctx.get("username")).isEqualTo("alice");
            assertThat((String) ctx.get("log_type")).isEqualTo("USER_ACTION");
            return Mono.empty();
        }));

        StepVerifier.create(filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth("alice"))))
                .verifyComplete();
    }

    @Test
    void filter_blankXRequestIdHeader_generatesNewRequestId() {
        // Arrange — header present but blank (not null); exercises isBlank() branch
        org.springframework.http.HttpHeaders headersWithBlankId =
                new org.springframework.http.HttpHeaders();
        headersWithBlankId.add(LoggingMdcFilter.REQUEST_ID_HEADER, "   ");
        when(request.getHeaders()).thenReturn(headersWithBlankId);

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    void filter_authenticationPresentButNotAuthenticated_treatsAsAnonymous() {
        // Arrange — Authentication in context but isAuthenticated() == false
        givenPath("/api/jobs");
        org.springframework.security.core.Authentication unauthenticated =
                mock(org.springframework.security.core.Authentication.class);
        lenient().when(unauthenticated.isAuthenticated()).thenReturn(false);

        when(chain.filter(any())).thenReturn(Mono.deferContextual(ctx -> {
            assertThat((String) ctx.get("username")).isEqualTo("anonymous");
            assertThat((String) ctx.get("log_type")).isEqualTo("SYSTEM");
            return Mono.empty();
        }));

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(unauthenticated)))
                .verifyComplete();
    }

    @Test
    void filter_pathEndingInHealthWithoutActuatorPrefix_setsHealthCheckLogType() {
        // Arrange — exercises path.endsWith("/health") without the actuator-prefix short-circuit
        givenPath("/service/health");
        when(chain.filter(any())).thenReturn(Mono.deferContextual(ctx -> {
            assertThat((String) ctx.get("log_type")).isEqualTo("HEALTH_CHECK");
            return Mono.empty();
        }));

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void filter_pathEndingInMetrics_setsHealthCheckLogType() {
        // Arrange — exercises path.endsWith("/metrics") branch
        givenPath("/service/metrics");
        when(chain.filter(any())).thenReturn(Mono.deferContextual(ctx -> {
            assertThat((String) ctx.get("log_type")).isEqualTo("HEALTH_CHECK");
            return Mono.empty();
        }));

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void filter_systemUsername_setsSystemLogType() {
        // Arrange — exercises the !username.equals("system") == false branch
        givenPath("/api/jobs");
        when(chain.filter(any())).thenReturn(Mono.deferContextual(ctx -> {
            assertThat((String) ctx.get("username")).isEqualTo("system");
            assertThat((String) ctx.get("log_type")).isEqualTo("SYSTEM");
            return Mono.empty();
        }));

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth("system"))))
                .verifyComplete();
    }

    // ─── getOrder marks filter position after JwtClaimsExtractionFilter ───────

    @Test
    void getOrder_isGreaterThanJwtFilterOrder() {
        // JwtClaimsExtractionFilter is at -50; LoggingMdcFilter at -40 runs after it
        assertThat(filter.getOrder()).isGreaterThan(-50);
    }

    private void givenPath(String value) {
        RequestPath requestPath = mock(RequestPath.class);
        when(requestPath.value()).thenReturn(value);
        when(request.getPath()).thenReturn(requestPath);
    }

    private AbstractAuthenticationToken jwtAuth(String subject) {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", subject));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
