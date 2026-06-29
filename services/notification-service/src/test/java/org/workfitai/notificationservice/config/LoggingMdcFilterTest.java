package org.workfitai.notificationservice.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LoggingMdcFilterTest {

    private final LoggingMdcFilter filter = new LoggingMdcFilter();

    @Test
    void doFilterInternal_usesProvidedHeaders_andClearsMdcAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications");
        request.addHeader(LoggingMdcFilter.REQUEST_ID_HEADER, "req-123");
        request.addHeader(LoggingMdcFilter.USERNAME_HEADER, "alice");
        request.addHeader(LoggingMdcFilter.ROLES_HEADER, "HR,ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader(LoggingMdcFilter.REQUEST_ID_HEADER)).isEqualTo("req-123");
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("username")).isNull();
    }

    @Test
    void doFilterInternal_generatesRequestId_andDefaultsAnonymous_whenHeadersMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(LoggingMdcFilter.REQUEST_ID_HEADER)).isNotBlank();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_detectsHealthCheckLogType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertThat(MDC.get("log_type")).isEqualTo("HEALTH_CHECK");

        filter.doFilterInternal(request, response, chain);
    }

    @Test
    void doFilterInternal_detectsAuthLogType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertThat(MDC.get("log_type")).isEqualTo("AUTH");

        filter.doFilterInternal(request, response, chain);
    }

    @Test
    void doFilterInternal_detectsUserActionLogType_whenAuthenticatedUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications");
        request.addHeader(LoggingMdcFilter.USERNAME_HEADER, "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertThat(MDC.get("log_type")).isEqualTo("USER_ACTION");

        filter.doFilterInternal(request, response, chain);
    }

    @Test
    void doFilterInternal_detectsSystemLogType_whenAnonymousAndNotSpecialPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertThat(MDC.get("log_type")).isEqualTo("SYSTEM");

        filter.doFilterInternal(request, response, chain);
    }

    @Test
    void doFilterInternal_clearsMdc_evenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new RuntimeException("downstream failure");
        };

        try {
            filter.doFilterInternal(request, response, chain);
        } catch (Exception ignored) {
            // expected
        }

        assertThat(MDC.get("requestId")).isNull();
    }
}
