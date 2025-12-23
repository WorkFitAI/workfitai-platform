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
 * GitHub OAuth2 provider implementation
 * Docs:
 * https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubOAuthService implements IOAuthProviderService {

    private final RestTemplate restTemplate;

    @Value("${app.oauth2.github.client-id}")
    private String clientId;

    @Value("${app.oauth2.github.client-secret}")
    private String clientSecret;

    private static final String AUTHORIZATION_URI = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URI = "https://github.com/login/oauth/access_token";
    private static final String USER_INFO_URI = "https://api.github.com/user";
    private static final String USER_EMAIL_URI = "https://api.github.com/user/emails";
    private static final String DEFAULT_SCOPE = "read:user user:email";

    @Override
    public String getAuthorizationUrl(String redirectUri, String state, String scope) {
        return UriComponentsBuilder.fromHttpUrl(AUTHORIZATION_URI)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scope != null ? scope : DEFAULT_SCOPE)
                .queryParam("state", state)
                .toUriString();
    }

    @Override
    public OAuthCallbackResponse handleCallback(String code, String redirectUri) {
        try {
            // Exchange code for tokens
            Map<String, Object> tokenResponse = exchangeCodeForToken(code, redirectUri);

            String accessToken = (String) tokenResponse.get("access_token");
            String tokenType = (String) tokenResponse.get("token_type");
            String scope = (String) tokenResponse.get("scope");

            // Get user info
            Map<String, Object> userInfo = getUserInfo(accessToken);
            String email = getVerifiedEmail(accessToken);

            return OAuthCallbackResponse.builder()
                    .token(accessToken)
                    .refreshToken(null) // GitHub doesn't support refresh tokens
                    .expiresIn(null) // GitHub tokens don't expire
                    .tokenType(tokenType)
                    .userInfo(OAuthCallbackResponse.UserInfo.builder()
                            .providerId(String.valueOf(userInfo.get("id")))
                            .email(email)
                            .name((String) userInfo.get("name"))
                            .picture((String) userInfo.get("avatar_url"))
                            .emailVerified(true) // GitHub only returns verified emails
                            .locale(null)
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("GitHub OAuth callback failed", e);
            throw new OAuthProviderException("Failed to authenticate with GitHub: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> refreshToken(String refreshToken) {
        throw new UnsupportedOperationException("GitHub does not support refresh tokens");
    }

    @Override
    public void revokeToken(String accessToken) {
        // GitHub OAuth tokens can only be revoked through the GitHub settings UI
        // or by deleting the OAuth app authorization
        log.warn("GitHub token revocation must be done manually through GitHub settings");
    }

    @Override
    public Provider getProvider() {
        return Provider.GITHUB;
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
        headers.set("Accept", "application/json"); // GitHub returns form-encoded by default

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(TOKEN_URI, request, Map.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new OAuthProviderException("Failed to exchange code for token");
        }

        // Check for error in response
        Map<String, Object> responseBody = response.getBody();
        if (responseBody.containsKey("error")) {
            throw new OAuthProviderException("GitHub error: " + responseBody.get("error_description"));
        }

        return responseBody;
    }

    /**
     * Get user info from GitHub
     */
    private Map<String, Object> getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                USER_INFO_URI, HttpMethod.GET, request, Map.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new OAuthProviderException("Failed to get user info from GitHub");
        }

        return response.getBody();
    }

    /**
     * Get primary verified email from GitHub
     * GitHub user profile may not include email, must fetch separately
     */
    private String getVerifiedEmail(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Map[]> response = restTemplate.exchange(
                USER_EMAIL_URI, HttpMethod.GET, request, Map[].class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new OAuthProviderException("Failed to get email from GitHub");
        }

        // Find primary verified email
        for (Map<String, Object> email : response.getBody()) {
            Boolean primary = (Boolean) email.get("primary");
            Boolean verified = (Boolean) email.get("verified");
            if (Boolean.TRUE.equals(primary) && Boolean.TRUE.equals(verified)) {
                return (String) email.get("email");
            }
        }

        // Fallback to first verified email
        for (Map<String, Object> email : response.getBody()) {
            Boolean verified = (Boolean) email.get("verified");
            if (Boolean.TRUE.equals(verified)) {
                return (String) email.get("email");
            }
        }

        throw new OAuthProviderException("No verified email found in GitHub account");
    }
}
