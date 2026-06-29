package org.workfitai.authservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

class RefreshCookieFactoryTest {

    private RefreshCookieFactory factory;

    @BeforeEach
    void setUp() {
        factory = new RefreshCookieFactory();
        ReflectionTestUtils.setField(factory, "env", "dev");
        ReflectionTestUtils.setField(factory, "secureOverride", "");
        ReflectionTestUtils.setField(factory, "sameSite", "Lax");
        ReflectionTestUtils.setField(factory, "path", "/auth");
    }

    // ─── build ────────────────────────────────────────────────────────────────

    @Test
    void build_setsHttpOnlyTrue() {
        ResponseCookie cookie = factory.build("refresh-token-value", 3600_000L);
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    void build_setsCorrectValue() {
        ResponseCookie cookie = factory.build("my-refresh-token", 3600_000L);
        assertThat(cookie.getValue()).isEqualTo("my-refresh-token");
    }

    @Test
    void build_setsPositiveMaxAge_forValidToken() {
        ResponseCookie cookie = factory.build("token", 604_800_000L);
        assertThat(cookie.getMaxAge().toMillis()).isEqualTo(604_800_000L);
    }

    @Test
    void build_setsSameSiteLax() {
        ResponseCookie cookie = factory.build("token", 3600_000L);
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    void build_setsPathToAuth() {
        ResponseCookie cookie = factory.build("token", 3600_000L);
        assertThat(cookie.getPath()).isEqualTo("/auth");
    }

    @Test
    void build_devEnvWithNoOverride_secureFalse() {
        // env=dev and no secureOverride → secure() returns false
        ResponseCookie cookie = factory.build("token", 3600_000L);
        assertThat(cookie.isSecure()).isFalse();
    }

    @Test
    void build_prodEnvWithNoOverride_secureTrue() {
        // Arrange
        ReflectionTestUtils.setField(factory, "env", "prod");

        // Act
        ResponseCookie cookie = factory.build("token", 3600_000L);

        // Assert
        assertThat(cookie.isSecure()).isTrue();
    }

    @Test
    void build_explicitSecureOverrideTrue_secureTrue() {
        // Arrange
        ReflectionTestUtils.setField(factory, "secureOverride", "true");

        // Act
        ResponseCookie cookie = factory.build("token", 3600_000L);

        // Assert
        assertThat(cookie.isSecure()).isTrue();
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_setsMaxAgeToZero() {
        ResponseCookie cookie = factory.delete();
        assertThat(cookie.getMaxAge().isZero()).isTrue();
    }

    @Test
    void delete_clearsValue() {
        ResponseCookie cookie = factory.delete();
        assertThat(cookie.getValue()).isEmpty();
    }

    @Test
    void delete_setsHttpOnlyTrue() {
        ResponseCookie cookie = factory.delete();
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    void delete_sameSiteMatchesBuild() {
        ResponseCookie build = factory.build("t", 1000L);
        ResponseCookie delete = factory.delete();
        assertThat(delete.getSameSite()).isEqualTo(build.getSameSite());
        assertThat(delete.getPath()).isEqualTo(build.getPath());
    }
}
