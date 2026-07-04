package org.workfitai.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.workfitai.apigateway.service.IOpaqueTokenService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class LogoutRevokeFilterImplTest {

    @Mock IOpaqueTokenService opaqueTokenService;
    @Mock ServerWebExchange exchange;
    @Mock ServerHttpRequest request;
    @Mock ServerHttpResponse response;
    @Mock GatewayFilterChain chain;

    LogoutRevokePostFilter.LogoutRevokeFilterImpl filter;

    @BeforeEach
    void setUp() {
        filter = new LogoutRevokePostFilter.LogoutRevokeFilterImpl(opaqueTokenService);

        RequestPath path = mock(RequestPath.class);
        lenient().when(path.value()).thenReturn("/api/data");
        lenient().when(exchange.getRequest()).thenReturn(request);
        lenient().when(exchange.getResponse()).thenReturn(response);
        lenient().when(request.getPath()).thenReturn(path);
        lenient().when(request.getHeaders()).thenReturn(HttpHeaders.EMPTY);
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    // ─── getOrder ─────────────────────────────────────────────────────────────

    @Test
    void getOrder_returnsLowestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    // ─── non-logout path ──────────────────────────────────────────────────────

    @Test
    void filter_nonLogoutPath_passesThrough() {
        // Act
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Assert — revoke never called for non-logout path
        verify(opaqueTokenService, never()).revokeAll(anyString());
    }

    // ─── logout path with 2xx response ────────────────────────────────────────

    @Test
    void filter_logoutPath_200Response_revokesAccessOpaque() {
        // Arrange
        setPath("/auth/logout");
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);
        when(exchange.getAttribute(OpaqueToJwtPreFilter.ATTR_OPAQUE_USED))
                .thenReturn("opaque-access-token");
        when(opaqueTokenService.revokeAll("opaque-access-token"))
                .thenReturn(Mono.just(1L));

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(opaqueTokenService).revokeAll("opaque-access-token");
    }

    @Test
    void filter_logoutPath_withRefreshHeader_revokesRefreshOpaque() {
        // Arrange
        setPath("/auth/logout");
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);
        when(exchange.getAttribute(OpaqueToJwtPreFilter.ATTR_OPAQUE_USED)).thenReturn(null);

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Refresh-Opaque", "opaque-refresh-token");
        when(request.getHeaders()).thenReturn(headers);
        when(opaqueTokenService.revokeAll("opaque-refresh-token")).thenReturn(Mono.just(1L));

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(opaqueTokenService).revokeAll("opaque-refresh-token");
    }

    @Test
    void filter_logoutPath_noOpaqueTokens_noRevokeCall() {
        // Arrange
        setPath("/auth/logout");
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);
        when(exchange.getAttribute(OpaqueToJwtPreFilter.ATTR_OPAQUE_USED)).thenReturn(null);

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(opaqueTokenService, never()).revokeAll(anyString());
    }

    // ─── logout path with 4xx/5xx response ───────────────────────────────────

    @Test
    void filter_logoutPath_4xxResponse_skipsRevoke() {
        // Arrange
        setPath("/auth/logout");
        when(response.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(opaqueTokenService, never()).revokeAll(anyString());
    }

    @Test
    void filter_logoutPath_5xxResponse_skipsRevoke() {
        // Arrange
        setPath("/auth/logout");
        when(response.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(opaqueTokenService, never()).revokeAll(anyString());
    }

    private void setPath(String pathValue) {
        RequestPath path = mock(RequestPath.class);
        when(path.value()).thenReturn(pathValue);
        when(request.getPath()).thenReturn(path);
    }
}
