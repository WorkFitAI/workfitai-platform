package org.workfitai.notificationservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebSocketHandshakeInterceptorTest {

    private final WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor();

    @Test
    void beforeHandshake_storesTokenFromQueryParameter() throws Exception {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addParameter("token", "jwt-token-value");
        ServerHttpRequest request = new ServletServerHttpRequest(mockRequest);
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, null, mock(WebSocketHandler.class), attributes);

        assertThat(result).isTrue();
        assertThat(attributes).containsEntry("token", "jwt-token-value");
    }

    @Test
    void beforeHandshake_continuesWithoutToken_whenQueryParameterMissing() throws Exception {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        ServerHttpRequest request = new ServletServerHttpRequest(mockRequest);
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, null, mock(WebSocketHandler.class), attributes);

        assertThat(result).isTrue();
        assertThat(attributes).doesNotContainKey("token");
    }

    @Test
    void beforeHandshake_continuesWithoutToken_whenTokenEmpty() throws Exception {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addParameter("token", "");
        ServerHttpRequest request = new ServletServerHttpRequest(mockRequest);
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, null, mock(WebSocketHandler.class), attributes);

        assertThat(result).isTrue();
        assertThat(attributes).doesNotContainKey("token");
    }

    @Test
    void beforeHandshake_returnsTrue_whenRequestNotServletBased() throws Exception {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, null, mock(WebSocketHandler.class), attributes);

        assertThat(result).isTrue();
        assertThat(attributes).isEmpty();
    }

    @Test
    void afterHandshake_doesNothing() {
        interceptor.afterHandshake(null, null, mock(WebSocketHandler.class), null);
        // no exception expected
    }
}
