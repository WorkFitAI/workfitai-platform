package org.workfitai.authservice.controller;

// Swagger annotations commented out for compilation
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
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

import java.util.Arrays;

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

    @PostMapping("/authorize/{provider}")
    public ResponseEntity<OAuthAuthorizeResponse> authorize(
            @PathVariable Provider provider,
            @Valid @RequestBody OAuthAuthorizeRequest request,
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

        OAuthAuthorizeResponse response = oauthService.authorize(provider, request, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/callback/{provider}")
    public ResponseEntity<OAuthCallbackResponse> callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state,
            @RequestParam(required = false) String redirectUri,
            HttpServletRequest request) {

        Provider providerEnum = Provider.valueOf(provider.toUpperCase());
        log.info("OAuth callback from provider: {}", providerEnum);
        OAuthCallbackResponse response = oauthService.handleCallback(providerEnum, code, state, redirectUri, request);
        return ResponseEntity.ok(response);
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
