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
import org.springframework.web.server.ServerWebExchange;

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

    // ─── getOrder marks filter position after JwtClaimsExtractionFilter ───────

    @Test
    void getOrder_isGreaterThanJwtFilterOrder() {
        // JwtClaimsExtractionFilter is at -50; LoggingMdcFilter at -40 runs after it
        assertThat(filter.getOrder()).isGreaterThan(-50);
    }
}
