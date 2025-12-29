package org.workfitai.authservice.controller;

// Swagger annotations commented out for compilation
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.constants.Messages;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.workfitai.authservice.dto.request.OAuthAuthorizeRequest;
import org.workfitai.authservice.dto.request.OAuthLinkRequest;
import org.workfitai.authservice.dto.response.AuthStatusResponse;
import org.workfitai.authservice.dto.response.LinkedAccountsResponse;
import org.workfitai.authservice.dto.response.OAuthAuthorizeResponse;
import org.workfitai.authservice.dto.response.OAuthCallbackResponse;
import org.workfitai.authservice.dto.response.OAuthLinkResponse;
import org.workfitai.authservice.dto.response.OAuthSession;
import org.workfitai.authservice.dto.response.ResponseData;
import org.workfitai.authservice.dto.response.TokensResponse;
import org.workfitai.authservice.enums.Provider;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.security.JwtService;
import org.workfitai.authservice.service.oauth.OAuthService;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Controller for OAuth2 authentication
 * Uses session exchange pattern: OAuth callback stores session in Redis,
 * redirects to frontend, frontend exchanges session for JSON tokens
 */
@Controller
@RequestMapping("/oauth")
@RequiredArgsConstructor
@Slf4j
public class OAuthController {

    private final OAuthService oauthService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${app.backend.base-url:http://localhost:9085}")
    private String backendBaseUrl;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    private static final String OAUTH_SESSION_PREFIX = "oauth:session:";

    @GetMapping("/authorize/{provider}")
    @ResponseBody
    public ResponseEntity<OAuthAuthorizeResponse> authorize(
            @PathVariable Provider provider,
//            @RequestBody(required = false) OAuthAuthorizeRequest request,
            @AuthenticationPrincipal(errorOnInvalidType = false) String username) {

        log.info("OAuth authorization request for provider: {} (authenticated: {})", provider, username != null);

        // If authenticated, lookup userId from username for LINK mode
        String userId = null;
        if (username != null) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                userId = user.getId();
                log.debug("Resolved username {} to userId: {}", username, userId);
            }
        }

        // If no request body provided, service will generate defaults (state,
        // redirectUri, scope)
//        OAuthAuthorizeResponse response = oauthService.authorize(provider, request, userId);
        OAuthAuthorizeResponse response = oauthService.authorize(provider, null, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * OAuth callback endpoint - receives callback from OAuth provider
     * Creates temporary session in Redis and redirects to frontend
     * Frontend will exchange session for actual tokens via /oauth/exchange
     */
    @GetMapping("/callback/{provider}")
    public void callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state,
            @RequestParam(required = false) String redirectUri,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String error_description,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        Provider providerEnum = Provider.valueOf(provider.toUpperCase());
        log.info("OAuth callback from provider: {}", providerEnum);

        // Validate required parameters
        if ((code == null || code.isBlank()) && error == null) {
            log.error("OAuth callback missing required parameters");
            redirectToFrontendWithError("Invalid callback request: missing code parameter", response);
            return;
        }

        if (state == null || state.isBlank()) {
            log.error("OAuth callback missing state parameter");
            redirectToFrontendWithError("Invalid callback request: missing state parameter", response);
            return;
        }

        // Handle OAuth provider errors (user denied, etc.)
        if (error != null) {
            log.warn("OAuth callback error from {}: {} - {}", providerEnum, error, error_description);
            String errorMessage = error_description != null ? error_description : "OAuth authentication failed";
            redirectToFrontendWithError(errorMessage, response);
            return;
        }

        try {
            OAuthCallbackResponse oauthResponse = oauthService.handleCallback(providerEnum, code, state, redirectUri,
                    request);

            // Check if LINK mode or LOGIN mode
            if ("LINK_SUCCESS".equals(oauthResponse.getTokenType())) {
                // LINK mode: Redirect to frontend with success message
                String redirectUrl = frontendBaseUrl + "/oauth-callback?status=link_success";
                log.info("OAuth account link successful, redirecting to: {}", redirectUrl);
                response.sendRedirect(redirectUrl);
                return;
            }

            // LOGIN mode: Create session and redirect to frontend
            String sessionId = createOAuthSession(oauthResponse);

            String redirectUrl = frontendBaseUrl + "/oauth-callback?session=" + sessionId;
            log.info("OAuth callback successful, redirecting to: {}", redirectUrl);
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.error("OAuth callback error for provider {}: {}", providerEnum, e.getMessage(), e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "OAuth authentication failed";
            redirectToFrontendWithError(errorMessage, response);
        }
    }

    /**
     * Create temporary OAuth session in Redis
     * @return session ID
     */
    private String createOAuthSession(OAuthCallbackResponse oauthResponse) {
        String sessionId = "oauth_sess_" + UUID.randomUUID().toString().replace("-", "");

        // Extract user info from JWT
        String username = jwtService.extractUsername(oauthResponse.getToken());
        var claims = jwtService.getClaims(oauthResponse.getToken());

        @SuppressWarnings("unchecked")
        var rolesObj = claims.get("roles");
        String companyId = claims.get("companyId") != null ? claims.get("companyId").toString() : null;

        // Build session object
        OAuthSession session = OAuthSession.builder()
                .accessToken(oauthResponse.getToken())
                .refreshToken(oauthResponse.getRefreshToken())
                .username(username)
                .roles(rolesObj != null ? (java.util.List<String>) rolesObj : java.util.Collections.emptyList())
                .companyId(companyId)
                .expiresIn(oauthResponse.getExpiresIn())
                .build();

        try {
            // Serialize session to JSON and store in Redis with 60 second TTL
            String sessionJson = objectMapper.writeValueAsString(session);
            String key = OAUTH_SESSION_PREFIX + sessionId;
            redisTemplate.opsForValue().set(key, sessionJson, 60, TimeUnit.SECONDS);

            log.info("Created OAuth session: {} for user: {}", sessionId, username);
            return sessionId;
        } catch (Exception e) {
            log.error("Failed to serialize OAuth session: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create OAuth session", e);
        }
    }

    /**
     * Redirect to frontend with error message
     */
    private void redirectToFrontendWithError(String errorMessage, HttpServletResponse response) throws IOException {
        String redirectUrl = frontendBaseUrl + "/oauth-callback?error=" +
                java.net.URLEncoder.encode(errorMessage, "UTF-8");
        response.sendRedirect(redirectUrl);
    }

    /**
     * Exchange OAuth session for tokens
     * Frontend calls this endpoint with session ID to get actual tokens
     * Session is one-time use and expires after 60 seconds
     */
    @GetMapping("/exchange")
    @ResponseBody
    public ResponseEntity<ResponseData<TokensResponse>> exchangeSession(
            @RequestParam String session,
            HttpServletResponse response) {

        log.info("OAuth session exchange requested: {}", session);

        // Retrieve session from Redis
        String key = OAUTH_SESSION_PREFIX + session;
        String sessionJson = redisTemplate.opsForValue().get(key);

        if (sessionJson == null || sessionJson.isBlank()) {
            log.warn("OAuth session not found or expired: {}", session);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired OAuth session");
        }

        // Delete session immediately (one-time use)
        redisTemplate.delete(key);

        // Deserialize session from JSON
        OAuthSession oauthSession;
        try {
            oauthSession = objectMapper.readValue(sessionJson, OAuthSession.class);
            log.info("OAuth session exchange successful for user: {}", oauthSession.getUsername());
        } catch (Exception e) {
            log.error("Failed to deserialize OAuth session: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process OAuth session");
        }

        // Set refresh token as HttpOnly cookie
        ResponseCookie cookie = ResponseCookie.from(Messages.Misc.REFRESH_TOKEN_COOKIE_NAME, oauthSession.getRefreshToken())
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofMillis(jwtService.getRefreshExpMs()))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Return JSON response (same format as login)
        // API Gateway will convert JWT access token to opaque token
        return ResponseEntity.ok()
                .body(ResponseData.success(
                        Messages.Success.TOKENS_ISSUED,
                        TokensResponse.withUserInfo(
                                oauthSession.getAccessToken(),  // JWT (will be converted to opaque)
                                oauthSession.getExpiresIn(),
                                oauthSession.getUsername(),
                                new java.util.HashSet<>(oauthSession.getRoles()),  // Convert List to Set
                                oauthSession.getCompanyId()
                        )
                ));
    }

    @PostMapping("/link/{provider}")
    @ResponseBody
    public ResponseEntity<OAuthLinkResponse> linkProvider(
            @PathVariable Provider provider,
            @Valid @RequestBody OAuthLinkRequest request,
            @AuthenticationPrincipal String username) {

        log.info("Linking {} account for user: {}", provider, username);
        OAuthLinkResponse response = oauthService.linkProvider(username, provider, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/unlink/{provider}")
    @ResponseBody
    public ResponseEntity<Void> unlinkProvider(
            @PathVariable Provider provider,
            @AuthenticationPrincipal String username) {

        log.info("Unlinking {} account for user: {}", provider, username);
        oauthService.unlinkProvider(username, provider);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/linked-accounts")
    @ResponseBody
    public ResponseEntity<LinkedAccountsResponse> getLinkedAccounts(
            @AuthenticationPrincipal String username) {

        log.info("Getting linked accounts for user: {}", username);
        LinkedAccountsResponse response = oauthService.getLinkedAccounts(username);
        return ResponseEntity.ok(response);
    }

    /**
     * Get user's authentication status
     * Shows hasPassword, OAuth providers, and whether can unlink
     */
    @GetMapping("/auth-status")
    @ResponseBody
    public ResponseEntity<AuthStatusResponse> getAuthStatus(
            @AuthenticationPrincipal String username) {

        log.info("Getting auth status for user: {}", username);
        AuthStatusResponse response = oauthService.getAuthStatus(username);
        return ResponseEntity.ok(response);
    }
}
