package org.workfitai.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

/**
 * Unit tests for LoggingMdcFilter.
 * Direct instantiation — no Spring context, no mocks required.
 * Verifies MDC population, log-type detection, and response header propagation.
 *
 * Note: JaCoCo excludes the config package and Filter classes, so these tests
 * enforce correctness rather than line-coverage metrics.
 */
class LoggingMdcFilterTest {

    private final LoggingMdcFilter filter = new LoggingMdcFilter();

    // ─── X-Request-Id header present ─────────────────────────────────────────

    @Test
    void doFilter_withRequestIdHeader_propagatesIdToResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        request.addHeader(LoggingMdcFilter.REQUEST_ID_HEADER, "req-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(LoggingMdcFilter.REQUEST_ID_HEADER)).isEqualTo("req-abc-123");
        assertThat(chain.getRequest()).isNotNull(); // chain.doFilter was called
    }

    // ─── X-Request-Id header absent — UUID generated ─────────────────────────

    @Test
    void doFilter_withoutRequestIdHeader_generatesRequestIdAndAddsToResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String responseId = response.getHeader(LoggingMdcFilter.REQUEST_ID_HEADER);
        assertThat(responseId).isNotNull().isNotBlank();
    }

    // ─── Anonymous user defaults ──────────────────────────────────────────────

    @Test
    void doFilter_noUsernameHeader_mdcUsernameDefaultsToAnonymous() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Use FilterChain lambda to capture MDC while filter is executing
        final String[] capturedUsername = new String[1];
        FilterChain capturingChain = (req, res) -> capturedUsername[0] = MDC.get("username");

        filter.doFilter(request, response, capturingChain);

        assertThat(capturedUsername[0]).isEqualTo("anonymous");
    }

    // ─── MDC cleared after filter execution ───────────────────────────────────

    @Test
    void doFilter_afterExecution_mdcIsCleared() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.addHeader(LoggingMdcFilter.USERNAME_HEADER, "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // finally block in doFilterInternal must clear all MDC keys
        assertThat(MDC.get("username")).isNull();
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("log_type")).isNull();
    }

    // ─── detectLogType: AUTH path ─────────────────────────────────────────────

    @Test
    void doFilter_loginPath_setsAuthLogType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        final String[] capturedLogType = new String[1];
        FilterChain capturingChain = (req, res) -> capturedLogType[0] = MDC.get("log_type");

        filter.doFilter(request, response, capturingChain);

        assertThat(capturedLogType[0]).isEqualTo("AUTH");
    }

    // ─── detectLogType: HEALTH_CHECK path ────────────────────────────────────

    @Test
    void doFilter_actuatorHealthPath_setsHealthCheckLogType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        final String[] capturedLogType = new String[1];
        FilterChain capturingChain = (req, res) -> capturedLogType[0] = MDC.get("log_type");

        filter.doFilter(request, response, capturingChain);

        assertThat(capturedLogType[0]).isEqualTo("HEALTH_CHECK");
    }

    // ─── detectLogType: USER_ACTION for authenticated non-anonymous user ──────

    @Test
    void doFilter_authenticatedUserOnBusinessPath_setsUserActionLogType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/profile");
        request.addHeader(LoggingMdcFilter.USERNAME_HEADER, "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();

        final String[] capturedLogType = new String[1];
        FilterChain capturingChain = (req, res) -> capturedLogType[0] = MDC.get("log_type");

        filter.doFilter(request, response, capturingChain);

        assertThat(capturedLogType[0]).isEqualTo("USER_ACTION");
    }

    // ─── detectLogType: SYSTEM for anonymous on non-auth path ────────────────

    @Test
    void doFilter_anonymousUserOnUnknownPath_setsSystemLogType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/unknown-path");
        // No X-Username header → defaults to "anonymous"
        MockHttpServletResponse response = new MockHttpServletResponse();

        final String[] capturedLogType = new String[1];
        FilterChain capturingChain = (req, res) -> capturedLogType[0] = MDC.get("log_type");

        filter.doFilter(request, response, capturingChain);

        assertThat(capturedLogType[0]).isEqualTo("SYSTEM");
    }
}
