package org.workfitai.cvservice.security.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * Utility class for Spring Security.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecurityUtils {
    public static final MacAlgorithm JWT_ALGORITHM = MacAlgorithm.HS512;

    /**
     * Get the login of the current user.
     *
     * @return the login of the current user from JWT.
     */

    public static Optional<String> getCurrentUserLogin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();

            // Ưu tiên lấy email
            String email = jwt.getClaimAsString("email");
            if (email != null) {
                return Optional.of(email);
            }

            // fallback sang username
            String username = jwt.getClaimAsString("username");
            if (username != null) {
                return Optional.of(username);
            }

            // cuối cùng fallback sang subject
            return Optional.ofNullable(jwt.getSubject());
        }
        return Optional.empty();
    }

    /**
     * Get the JWT of the current user. This method is used for forwarding JWT
     *
     * @return the JWT of the current user.
     */
    public static Optional<String> getCurrentUserJWT() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth.getToken().getTokenValue());
        }
        return Optional.empty();
    }
}
