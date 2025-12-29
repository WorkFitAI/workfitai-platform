package org.workfitai.authservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Temporary session data stored in Redis during OAuth flow
 * Used for session exchange pattern to deliver tokens to frontend
 * TTL: 60 seconds (one-time use)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * JWT access token (will be converted to opaque by API Gateway)
     */
    private String accessToken;

    /**
     * JWT refresh token (will be set as HttpOnly cookie)
     */
    private String refreshToken;

    /**
     * Username/email of authenticated user
     */
    private String username;

    /**
     * User roles (CANDIDATE, HR, ADMIN, etc.)
     */
    private List<String> roles;

    /**
     * Company ID (null for candidates)
     */
    private String companyId;

    /**
     * Access token expiration in milliseconds
     */
    private long expiresIn;
}
