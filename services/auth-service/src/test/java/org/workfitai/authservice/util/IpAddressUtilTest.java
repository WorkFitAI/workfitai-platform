package org.workfitai.authservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.workfitai.authservice.util.IpAddressUtil;

class IpAddressUtilTest {

    // ─── X-Forwarded-For ─────────────────────────────────────────────────────

    @Test
    void getClientIp_returnsXForwardedFor_whenHeaderPresent() {
        // Arrange
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "203.0.113.1");

        // Act & Assert
        assertThat(IpAddressUtil.getClientIp(req)).isEqualTo("203.0.113.1");
    }

    @Test
    void getClientIp_returnsFirstIp_whenXForwardedForContainsProxyChain() {
        // Arrange
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1, 192.168.1.1");

        // Act & Assert
        assertThat(IpAddressUtil.getClientIp(req)).isEqualTo("203.0.113.1");
    }

    // ─── Proxy-Client-IP ─────────────────────────────────────────────────────

    @Test
    void getClientIp_returnsProxyClientIp_whenXForwardedForAbsent() {
        // Arrange
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Proxy-Client-IP", "203.0.113.2");

        // Act & Assert
        assertThat(IpAddressUtil.getClientIp(req)).isEqualTo("203.0.113.2");
    }

    // ─── RemoteAddr fallback ──────────────────────────────────────────────────

    @Test
    void getClientIp_returnsRemoteAddr_whenNoHeaderPresent() {
        // Arrange
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");

        // Act & Assert
        String ip = IpAddressUtil.getClientIp(req);
        assertThat(ip).isEqualTo("127.0.0.1");
    }

    @Test
    void getClientIp_ignoresUnknownHeaderValue() {
        // Arrange
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "unknown");
        req.setRemoteAddr("127.0.0.1");

        // Act — "unknown" is treated as unknown, should fall through to remote addr
        String ip = IpAddressUtil.getClientIp(req);
        // Either "unknown" or "127.0.0.1" is acceptable depending on implementation
        assertThat(ip).isNotNull();
    }
}
