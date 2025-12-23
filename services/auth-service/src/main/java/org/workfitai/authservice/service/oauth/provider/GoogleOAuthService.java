package org.workfitai.authservice.service.oauth.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.workfitai.authservice.dto.response.OAuthCallbackResponse;
import org.workfitai.authservice.enums.Provider;
import org.workfitai.authservice.exception.OAuthProviderException;

import java.util.Map;

/**
 * Google OAuth2 provider implementation
 * Docs: https://developers.google.com/identity/protocols/oauth2/web-server
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService implements IOAuthProviderService {

    private final RestTemplate restTemplate;

    @Value("${app.oauth2.google.client-id}")
    private String clientId;

    @Value("${app.oauth2.google.client-secret}")
    private String clientSecret;

    private static final String AUTHORIZATION_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URI = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String REVOKE_URI = "https://oauth2.googleapis.com/revoke";
    private static final String DEFAULT_SCOPE = "openid profile email";

    @Override
    public String getAuthorizationUrl(String redirectUri, String state, String scope) {
        return UriComponentsBuilder.fromHttpUrl(AUTHORIZATION_URI)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scope != null ? scope : DEFAULT_SCOPE)
                .queryParam("state", state)
                .queryParam("access_type", "offline") // Get refresh token
                .queryParam("prompt", "consent") // Force consent to get refresh token
                .toUriString();
    }

    @Override
    public OAuthCallbackResponse handleCallback(String code, String redirectUri) {
        try {
            // Exchange code for tokens
            Map<String, Object> tokenResponse = exchangeCodeForToken(code, redirectUri);

            String accessToken = (String) tokenResponse.get("access_token");
            String refreshToken = (String) tokenResponse.get("refresh_token");
            Integer expiresIn = (Integer) tokenResponse.get("expires_in");
            String tokenType = (String) tokenResponse.get("token_type");

            // Get user info
            Map<String, Object> userInfo = getUserInfo(accessToken);

            return OAuthCallbackResponse.builder()
                    .token(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(expiresIn != null ? expiresIn.longValue() : 3600L)
                    .tokenType(tokenType)
                    .userInfo(OAuthCallbackResponse.UserInfo.builder()
                            .providerId((String) userInfo.get("id"))
                            .email((String) userInfo.get("email"))
                            .name((String) userInfo.get("name"))
                            .picture((String) userInfo.get("picture"))
                            .emailVerified((Boolean) userInfo.get("verified_email"))
                            .locale((String) userInfo.get("locale"))
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("Google OAuth callback failed", e);
            throw new OAuthProviderException("Failed to authenticate with Google: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> refreshToken(String refreshToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("refresh_token", refreshToken);
            body.add("grant_type", "refresh_token");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(TOKEN_URI, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }

            throw new OAuthProviderException("Failed to refresh Google token");

        } catch (Exception e) {
            log.error("Failed to refresh Google token", e);
            throw new OAuthProviderException("Failed to refresh Google token: " + e.getMessage());
        }
    }

    @Override
    public void revokeToken(String accessToken) {
        try {
            String revokeUrl = REVOKE_URI + "?token=" + accessToken;
            restTemplate.postForEntity(revokeUrl, null, Void.class);
            log.info("Successfully revoked Google token");
        } catch (Exception e) {
            log.warn("Failed to revoke Google token", e);
            // Don't throw exception, just log warning
        }
    }

    @Override
    public Provider getProvider() {
        return Provider.GOOGLE;
    }

    @Override
    public String getDefaultScope() {
        return DEFAULT_SCOPE;
    }

    /**
     * Exchange authorization code for access token
     */
    private Map<String, Object> exchangeCodeForToken(String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(TOKEN_URI, request, Map.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new OAuthProviderException("Failed to exchange code for token");
        }

        return response.getBody();
    }

    /**
     * Get user info from Google
     */
    private Map<String, Object> getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                USER_INFO_URI, HttpMethod.GET, request, Map.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new OAuthProviderException("Failed to get user info from Google");
        }

        return response.getBody();
    }
}
