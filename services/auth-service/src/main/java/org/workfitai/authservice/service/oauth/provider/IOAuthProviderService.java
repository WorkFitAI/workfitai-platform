package org.workfitai.authservice.service.oauth.provider;

import org.workfitai.authservice.dto.response.OAuthCallbackResponse;
import org.workfitai.authservice.enums.Provider;

import java.util.Map;

/**
 * Interface for OAuth provider implementations (Google, GitHub)
 */
public interface IOAuthProviderService {

    /**
     * Get authorization URL for OAuth flow
     *
     * @param redirectUri Callback URL after authorization
     * @param state       CSRF protection state
     * @param scope       Requested permissions
     * @return Authorization URL to redirect user
     */
    String getAuthorizationUrl(String redirectUri, String state, String scope);

    /**
     * Exchange authorization code for access token
     *
     * @param code        Authorization code from OAuth callback
     * @param redirectUri Same redirect URI used in authorization
     * @return OAuth callback response with tokens and user info
     */
    OAuthCallbackResponse handleCallback(String code, String redirectUri);

    /**
     * Refresh access token using refresh token
     *
     * @param refreshToken Current refresh token
     * @return New tokens
     */
    Map<String, Object> refreshToken(String refreshToken);

    /**
     * Revoke OAuth tokens (logout from provider)
     *
     * @param accessToken Token to revoke
     */
    void revokeToken(String accessToken);

    /**
     * Get provider type
     */
    Provider getProvider();

    /**
     * Get default scope for this provider
     */
    String getDefaultScope();
}
