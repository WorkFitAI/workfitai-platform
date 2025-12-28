package org.workfitai.authservice.controller;

// Swagger annotations commented out for compilation
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;
import org.workfitai.authservice.dto.request.OAuthAuthorizeRequest;
import org.workfitai.authservice.dto.request.OAuthLinkRequest;
import org.workfitai.authservice.dto.response.AuthStatusResponse;
import org.workfitai.authservice.dto.response.LinkedAccountsResponse;
import org.workfitai.authservice.dto.response.OAuthAuthorizeResponse;
import org.workfitai.authservice.dto.response.OAuthCallbackResponse;
import org.workfitai.authservice.dto.response.OAuthLinkResponse;
import org.workfitai.authservice.enums.Provider;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.service.oauth.OAuthService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * REST controller for OAuth2 authentication
 */
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
@Slf4j
public class OAuthController {

    private final OAuthService oauthService;
    private final UserRepository userRepository;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${app.backend.base-url:http://localhost:9085}")
    private String backendBaseUrl;

    @PostMapping("/authorize/{provider}")
    public ResponseEntity<OAuthAuthorizeResponse> authorize(
            @PathVariable Provider provider,
            @RequestBody(required = false) OAuthAuthorizeRequest request,
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
        OAuthAuthorizeResponse response = oauthService.authorize(provider, request, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/callback/{provider}")
    public RedirectView callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state,
            @RequestParam(required = false) String redirectUri,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String error_description,
            HttpServletRequest request) {

        Provider providerEnum = Provider.valueOf(provider.toUpperCase());
        log.info("OAuth callback from provider: {}", providerEnum);

        // Handle OAuth provider errors (user denied, etc.)
        if (error != null) {
            log.warn("OAuth callback error from {}: {} - {}", providerEnum, error, error_description);
            String errorMessage = error_description != null ? error_description : "OAuth authentication failed";
            return redirectToFrontend(null, null, errorMessage, "error");
        }

        try {
            OAuthCallbackResponse response = oauthService.handleCallback(providerEnum, code, state, redirectUri,
                    request);

            // Check if LINK mode or LOGIN mode
            if ("LINK_SUCCESS".equals(response.getTokenType())) {
                // LINK mode: Redirect to profile/settings with success message
                return redirectToFrontend(null, null, null, "link_success");
            } else {
                // LOGIN mode: Redirect to dashboard/home with tokens
                return redirectToFrontend(response.getToken(), response.getRefreshToken(), null, "success");
            }

        } catch (Exception e) {
            log.error("OAuth callback error for provider {}: {}", providerEnum, e.getMessage(), e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "OAuth authentication failed";
            return redirectToFrontend(null, null, errorMessage, "error");
        }
    }

    /**
     * Redirect to API Gateway OAuth endpoint (which converts JWT→opaque, then
     * redirects to frontend)
     */
    private RedirectView redirectToFrontend(String accessToken, String refreshToken, String error, String status) {
        // Redirect to Gateway endpoint instead of directly to frontend
        // Gateway will convert JWT tokens to opaque tokens before forwarding to
        // frontend
        StringBuilder url = new StringBuilder(backendBaseUrl);
        url.append("/auth/oauth/success?status=").append(status);

        if (accessToken != null) {
            url.append("&accessToken=").append(URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
        }

        if (refreshToken != null) {
            url.append("&refreshToken=").append(URLEncoder.encode(refreshToken, StandardCharsets.UTF_8));
        }

        if (error != null) {
            url.append("&error=").append(URLEncoder.encode(error, StandardCharsets.UTF_8));
        }

        log.info("Redirecting to Gateway OAuth endpoint: {} (status={})",
                backendBaseUrl + "/auth/oauth/success", status);

        RedirectView redirectView = new RedirectView(url.toString());
        redirectView.setStatusCode(HttpStatus.FOUND); // 302 redirect
        return redirectView;
    }

    @PostMapping("/link/{provider}")
    public ResponseEntity<OAuthLinkResponse> linkProvider(
            @PathVariable Provider provider,
            @Valid @RequestBody OAuthLinkRequest request,
            @AuthenticationPrincipal String username) {

        log.info("Linking {} account for user: {}", provider, username);
        OAuthLinkResponse response = oauthService.linkProvider(username, provider, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/unlink/{provider}")
    public ResponseEntity<Void> unlinkProvider(
            @PathVariable Provider provider,
            @AuthenticationPrincipal String username) {

        log.info("Unlinking {} account for user: {}", provider, username);
        oauthService.unlinkProvider(username, provider);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/linked-accounts")
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
    public ResponseEntity<AuthStatusResponse> getAuthStatus(
            @AuthenticationPrincipal String username) {

        log.info("Getting auth status for user: {}", username);
        AuthStatusResponse response = oauthService.getAuthStatus(username);
        return ResponseEntity.ok(response);
    }
}
