package org.workfitai.jobservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoggingMdcFilterTest {

    private final LoggingMdcFilter filter = new LoggingMdcFilter();

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @Test
    void doFilterInternal_usesProvidedRequestIdAndUsername_andSetsUserActionLogType() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("req-123");
        when(request.getHeader("X-Username")).thenReturn("alice");
        when(request.getHeader("X-User-Roles")).thenReturn("HR");
        when(request.getRequestURI()).thenReturn("/api/hr/jobs");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response).addHeader("X-Request-Id", "req-123");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void doFilterInternal_generatesRequestId_whenHeaderBlank() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("");
        when(request.getHeader("X-Username")).thenReturn(null);
        when(request.getHeader("X-User-Roles")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/health");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_detectsAuthEndpoint() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("req-1");
        when(request.getHeader("X-Username")).thenReturn("anonymous");
        when(request.getHeader("X-User-Roles")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/auth/login");
        when(request.getMethod()).thenReturn("POST");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_detectsActuatorEndpoint() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("req-2");
        when(request.getHeader("X-Username")).thenReturn("system");
        when(request.getHeader("X-User-Roles")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/actuator/health");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_clearsMdc_evenWhenFilterChainThrows() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("req-3");
        when(request.getHeader("X-Username")).thenReturn("bob");
        when(request.getHeader("X-User-Roles")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/jobs");
        when(request.getMethod()).thenReturn("GET");
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(filterChain).doFilter(request, response);

        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException ignored) {
        }

        assertThat(MDC.get("requestId")).isNull();
    }
}
