package org.workfitai.authservice.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for OAuth authorization URL
 */
@Data
@Builder
public class OAuthAuthorizeResponse {

    private String authorizationUrl;

    private String state;

    private String provider;

    /**
     * State expiry in seconds (default 300s = 5 minutes)
     */
    private Long expiresIn;
}
