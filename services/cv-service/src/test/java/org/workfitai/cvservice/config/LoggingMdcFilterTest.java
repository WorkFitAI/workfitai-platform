package org.workfitai.cvservice.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LoggingMdcFilterTest {

    private final LoggingMdcFilter filter = new LoggingMdcFilter();

    /** Captures MDC state while the chain is executing, since the filter clears MDC right after. */
    private String[] captureMdcDuringChain(FilterChain chain, String... keys) throws Exception {
        String[] captured = new String[keys.length];
        doAnswer(invocation -> {
            for (int i = 0; i < keys.length; i++) {
                captured[i] = MDC.get(keys[i]);
            }
            return null;
        }).when(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        return captured;
    }

    @Test
    void doFilterInternal_generatesRequestId_whenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/candidate/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(LoggingMdcFilter.REQUEST_ID_HEADER)).isNotBlank();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_reusesProvidedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/candidate/123");
        request.addHeader(LoggingMdcFilter.REQUEST_ID_HEADER, "req-fixed-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(LoggingMdcFilter.REQUEST_ID_HEADER)).isEqualTo("req-fixed-id");
    }

    @Test
    void doFilterInternal_detectsHealthCheckLogType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        String[] logType = captureMdcDuringChain(chain, "log_type");

        filter.doFilterInternal(request, response, chain);

        assertThat(logType[0]).isEqualTo("HEALTH_CHECK");
    }

    @Test
    void doFilterInternal_detectsAuthLogType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        String[] logType = captureMdcDuringChain(chain, "log_type");

        filter.doFilterInternal(request, response, chain);

        assertThat(logType[0]).isEqualTo("AUTH");
    }

    @Test
    void doFilterInternal_detectsUserActionLogType_whenUsernameHeaderPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/candidate/alice");
        request.addHeader(LoggingMdcFilter.USERNAME_HEADER, "alice");
        request.addHeader(LoggingMdcFilter.ROLES_HEADER, "CANDIDATE");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        String[] captured = captureMdcDuringChain(chain, "log_type", "username", "roles");

        filter.doFilterInternal(request, response, chain);

        assertThat(captured[0]).isEqualTo("USER_ACTION");
        assertThat(captured[1]).isEqualTo("alice");
        assertThat(captured[2]).isEqualTo("CANDIDATE");
    }

    @Test
    void doFilterInternal_defaultsToAnonymous_whenUsernameHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/candidate/alice");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        String[] captured = captureMdcDuringChain(chain, "log_type", "username");

        filter.doFilterInternal(request, response, chain);

        assertThat(captured[0]).isEqualTo("SYSTEM");
        assertThat(captured[1]).isEqualTo("anonymous");
    }

    @Test
    void doFilterInternal_clearsMdc_afterChainCompletes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/candidate/alice");
        request.addHeader(LoggingMdcFilter.USERNAME_HEADER, "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(MDC.get("log_type")).isNull();
        assertThat(MDC.get("username")).isNull();
    }
}
