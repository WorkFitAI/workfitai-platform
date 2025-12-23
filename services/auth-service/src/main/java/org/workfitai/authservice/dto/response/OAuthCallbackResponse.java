package org.workfitai.authservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response DTO after successful OAuth callback
 */
@Data
@Builder
public class OAuthCallbackResponse {

    private String token; // JWT access token

    private String refreshToken; // JWT refresh token

    private Long expiresIn; // Token expiry in seconds

    private String tokenType; // "Bearer"

    private UserInfo userInfo;

    @Data
    @Builder
    public static class UserInfo {
        private String providerId; // OAuth provider's user ID
        private String email;
        private String name; // Full name from OAuth provider
        private String picture; // Profile picture URL
        private Boolean emailVerified; // Email verification status
        private String locale; // User's locale/language
    }
}
