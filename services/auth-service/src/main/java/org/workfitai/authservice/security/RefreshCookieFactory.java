package org.workfitai.authservice.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.workfitai.authservice.constants.Messages;

/**
 * Single source of truth for the refresh-token (RT) cookie.
 *
 * <p>All RT cookies (login, 2FA login, refresh rotation, logout delete, OAuth
 * exchange) MUST be built here so attributes never drift between endpoints.
 *
 * <p>Attribute derivation:
 * <ul>
 *   <li>{@code Secure} - explicit {@code app.cookie.secure} if set, otherwise
 *       derived from {@code app.env} ({@code prod} -&gt; true). Keeping it off in
 *       dev lets the cookie be set over plain http://localhost.</li>
 *   <li>{@code SameSite} - {@code app.cookie.same-site} (default {@code Lax}).
 *       FE and BE share the same registrable domain (same-site), so {@code Lax}
 *       is sent on cross-origin fetches while still protecting /auth/refresh
 *       against CSRF. Do not use {@code None} without adding CSRF tokens.</li>
 *   <li>{@code Path} - {@code app.cookie.path} (default {@code /auth}), scoping
 *       the cookie to the auth endpoints behind the gateway.</li>
 * </ul>
 */
@Component
public class RefreshCookieFactory {

    @Value("${app.env:dev}")
    private String env;

    /** Blank -> Secure derived from {@link #env}; otherwise parsed as boolean. */
    @Value("${app.cookie.secure:}")
    private String secureOverride;

    @Value("${app.cookie.same-site:Lax}")
    private String sameSite;

    @Value("${app.cookie.path:/auth}")
    private String path;

    private boolean secure() {
        if (secureOverride != null && !secureOverride.isBlank()) {
            return Boolean.parseBoolean(secureOverride.trim());
        }
        return "prod".equalsIgnoreCase(env);
    }

    /** Build the RT cookie carrying a refresh token value. */
    public ResponseCookie build(String value, long maxAgeMs) {
        return ResponseCookie.from(Messages.Misc.REFRESH_TOKEN_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure())
                .sameSite(sameSite)
                .path(path)
                .maxAge(Duration.ofMillis(maxAgeMs))
                .build();
    }

    /**
     * Build the RT deletion cookie. Attributes MUST match {@link #build} (same
     * Path/SameSite/Secure) or browsers may not clear the original cookie.
     */
    public ResponseCookie delete() {
        return ResponseCookie.from(Messages.Misc.REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure())
                .sameSite(sameSite)
                .path(path)
                .maxAge(0)
                .build();
    }
}
