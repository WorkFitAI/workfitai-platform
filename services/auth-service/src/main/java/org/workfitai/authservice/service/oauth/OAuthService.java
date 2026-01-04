package org.workfitai.authservice.service.oauth;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.authservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.authservice.dto.request.OAuthAuthorizeRequest;
import org.workfitai.authservice.dto.request.OAuthLinkRequest;
import org.workfitai.authservice.dto.response.AuthStatusResponse;
import org.workfitai.authservice.dto.response.LinkedAccountsResponse;
import org.workfitai.authservice.dto.response.LinkedProviderResponse;
import org.workfitai.authservice.dto.response.OAuthAuthorizeResponse;
import org.workfitai.authservice.dto.response.OAuthCallbackResponse;
import org.workfitai.authservice.dto.response.OAuthLinkResponse;
import org.workfitai.authservice.enums.EventType;
import org.workfitai.authservice.enums.Provider;
import org.workfitai.authservice.enums.UserRole;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.exception.InvalidOAuthStateException;
import org.workfitai.authservice.exception.NotFoundException;
import org.workfitai.authservice.exception.OAuthProviderException;
import org.workfitai.authservice.messaging.UserRegistrationProducer;
import org.workfitai.authservice.model.OAuthProvider;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.security.JwtService;
import org.workfitai.authservice.service.RefreshTokenService;
import org.workfitai.authservice.service.SessionService;
import org.workfitai.authservice.service.oauth.provider.GitHubOAuthService;
import org.workfitai.authservice.service.oauth.provider.GoogleOAuthService;
import org.workfitai.authservice.service.oauth.provider.IOAuthProviderService;
import org.workfitai.authservice.util.IpAddressUtil;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Main OAuth service orchestrating OAuth flows
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthService {

    private final GoogleOAuthService googleOAuthService;
    private final GitHubOAuthService gitHubOAuthService;
    private final OAuthProviderService oauthProviderService;
    private final OAuthEventPublisher oauthEventPublisher;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SessionService sessionService;
    private final UserRegistrationProducer userRegistrationProducer;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${app.backend.base-url:http://localhost:9085}")
    private String backendBaseUrl;

    private static final String OAUTH_STATE_PREFIX = "oauth:state:";
    private static final long STATE_EXPIRATION_MINUTES = 10;

    /**
     * Generate authorization URL for OAuth flow
     * If request is null or fields are null, generates defaults:
     * - state: random UUID
     * - redirectUri: backend base URL + /auth/oauth/callback/{provider}
     * - scope: provider defaults
     */
    public OAuthAuthorizeResponse authorize(Provider provider, OAuthAuthorizeRequest request, String userId) {
        IOAuthProviderService providerService = getProviderService(provider);

        // Handle null request - create default
        if (request == null) {
            request = OAuthAuthorizeRequest.builder().build();
        }

        // Generate state if not provided (random UUID)
        String clientState = request.getState();
        if (clientState == null || clientState.isBlank()) {
            clientState = UUID.randomUUID().toString();
            log.debug("Generated random state: {}", clientState);
        }

        // Use default redirectUri if not provided
        String redirectUri = request.getRedirectUri();
        if (redirectUri == null || redirectUri.isBlank()) {
            // Default: {backend-base-url}/auth/oauth/callback/{provider}
            // This is where OAuth provider (Google/GitHub) will redirect after user
            // authorizes
            redirectUri = getBackendBaseUrl() + "/auth/oauth/callback/" + provider.name().toLowerCase();
            log.debug("Using default redirect URI: {}", redirectUri);
        }

        // Generate and store CSRF state with userId context for LINK mode
        String state = generateState(provider, clientState, userId);
        storeState(state, redirectUri);

        // Use custom scope or provider defaults
        String scope = request.getScope() != null && !request.getScope().isEmpty()
                ? String.join(" ", request.getScope())
                : providerService.getDefaultScope();

        String authUrl = providerService.getAuthorizationUrl(redirectUri, state, scope);

        log.info("Generated OAuth authorization URL for provider: {} (LINK mode: {}, redirect: {})",
                provider, userId != null, redirectUri);

        return OAuthAuthorizeResponse.builder()
                .authorizationUrl(authUrl)
                .state(state)
                .provider(provider.name())
                .expiresIn(STATE_EXPIRATION_MINUTES * 60)
                .build();
    }

    /**
     * Handle OAuth callback and create/login user OR link provider
     */
    @Transactional
    public OAuthCallbackResponse handleCallback(Provider provider, String code, String state,
            String redirectUri, HttpServletRequest request) {
        // Validate CSRF state and get mode + userId
        OAuthStateInfo stateInfo = validateState(state);
        String finalRedirectUri = redirectUri != null ? redirectUri : stateInfo.redirectUri();

        IOAuthProviderService providerService = getProviderService(provider);

        // Exchange code for tokens and user info
        OAuthCallbackResponse response = providerService.handleCallback(code, finalRedirectUri);
        OAuthCallbackResponse.UserInfo userInfo = response.getUserInfo();

        // Check mode: LINK or LOGIN
        if ("LINK".equals(stateInfo.mode()) && stateInfo.userId() != null) {
            // LINK mode: Link provider to existing user
            return handleLinkMode(stateInfo.userId(), provider, response, userInfo, request);
        } else {
            // LOGIN mode: Find or create user and login
            return handleLoginMode(provider, response, userInfo, request);
        }
    }

    /**
     * Handle LOGIN mode: Create/login user
     */
    private OAuthCallbackResponse handleLoginMode(Provider provider, OAuthCallbackResponse response,
            OAuthCallbackResponse.UserInfo userInfo, HttpServletRequest request) {
        // Find or create user
        User user = findOrCreateUser(provider, userInfo);

        // Save or update OAuth provider connection
        saveOAuthProvider(user, provider, response, userInfo);

        // Update user's last OAuth login
        updateLastOAuthLogin(user, provider, request);

        // Generate JWT tokens - need to create UserDetails from User
        String[] authorities = user.getRoles() != null ? user.getRoles().toArray(new String[0])
                : new String[] { "CANDIDATE" };
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword() != null ? user.getPassword() : "")
                .authorities(authorities)
                .build();

        String accessToken = jwtService.generateAccessToken(userDetails);
        String jti = jwtService.newJti();
        String refreshToken = jwtService.generateRefreshTokenWithJti(userDetails, jti);

        // Store refresh token JTI in Redis (use OAuth provider as deviceId)
        String deviceId = provider.name().toLowerCase() + "-web";
        refreshTokenService.saveJti(user.getId(), deviceId, jti);

        // Create session in MongoDB
        sessionService.createSession(user.getId(), jti, jwtService.getRefreshExpMs(), request, null, null);

        // Publish login event
        String ipAddress = IpAddressUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        oauthEventPublisher.publishLoginEvent(user, provider, ipAddress, userAgent);

        log.info("OAuth callback successful for user: {} via {} (session created)", user.getUsername(), provider);

        // Return response with JWT tokens (not OAuth tokens)
        return OAuthCallbackResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessExpMs()) // 15 minutes
                .tokenType("Bearer")
                .userInfo(userInfo)
                .build();
    }

    /**
     * Handle LINK mode: Link provider to existing user
     */
    private OAuthCallbackResponse handleLinkMode(String userId, Provider provider,
            OAuthCallbackResponse response, OAuthCallbackResponse.UserInfo userInfo, HttpServletRequest request) {
        // Get existing user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new OAuthProviderException("User not found"));

        // Check if this OAuth account is already linked to another user
        Optional<OAuthProvider> existingProvider = oauthProviderService
                .findByProviderAndProviderId(provider, userInfo.getProviderId());

        if (existingProvider.isPresent() && !existingProvider.get().getUserId().equals(userId)) {
            throw new OAuthProviderException("This " + provider + " account is already linked to another user");
        }

        // Link provider
        OAuthProvider oauthProvider = buildOAuthProvider(user, provider, response, userInfo);
        oauthProviderService.linkProvider(userId, oauthProvider);

        // Update user's OAuth providers set
        if (user.getOauthProviders() == null) {
            user.setOauthProviders(new HashSet<>());
        }
        user.getOauthProviders().add(provider.name());
        userRepository.save(user);

        // Publish event
        oauthEventPublisher.publishAccountLinkedEvent(user, oauthProvider);

        log.info("Linked {} account to user: {} via callback", provider, user.getUsername());

        // Return success response (no new tokens needed, user already authenticated)
        return OAuthCallbackResponse.builder()
                .token(null) // No token - user already has valid session
                .refreshToken(null)
                .expiresIn(null)
                .tokenType("LINK_SUCCESS")
                .userInfo(userInfo)
                .build();
    }

    /**
     * Link OAuth provider to existing user account (deprecated - use callback LINK
     * mode instead)
     */
    @Transactional
    public OAuthLinkResponse linkProvider(String username, Provider provider, OAuthLinkRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new OAuthProviderException("User not found"));

        IOAuthProviderService providerService = getProviderService(provider);

        // Exchange code for tokens and user info
        OAuthCallbackResponse response = providerService.handleCallback(request.getCode(), request.getRedirectUri());
        OAuthCallbackResponse.UserInfo userInfo = response.getUserInfo();

        // Check if this OAuth account is already linked to another user
        Optional<OAuthProvider> existingProvider = oauthProviderService
                .findByProviderAndProviderId(provider, userInfo.getProviderId());

        if (existingProvider.isPresent() && !existingProvider.get().getUserId().equals(user.getId())) {
            throw new OAuthProviderException("This " + provider + " account is already linked to another user");
        }

        // Link provider
        OAuthProvider oauthProvider = buildOAuthProvider(user, provider, response, userInfo);
        oauthProviderService.linkProvider(user.getId(), oauthProvider);

        // Update user's OAuth providers set
        if (user.getOauthProviders() == null) {
            user.setOauthProviders(new HashSet<>());
        }
        user.getOauthProviders().add(provider.name());
        userRepository.save(user);

        // Publish event
        oauthEventPublisher.publishAccountLinkedEvent(user, oauthProvider);

        log.info("Linked {} account to user: {}", provider, username);

        return OAuthLinkResponse.builder()
                .success(true)
                .provider(provider.name())
                .email(userInfo.getEmail())
                .displayName(userInfo.getName())
                .linkedAt(Instant.now())
                .build();
    }

    /**
     * Unlink OAuth provider from user account
     */
    @Transactional
    public void unlinkProvider(String username, Provider provider) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new OAuthProviderException("User not found"));

        // Find provider to get email for event
        OAuthProvider oauthProvider = oauthProviderService.findByUserId(user.getId()).stream()
                .filter(p -> p.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new OAuthProviderException("Provider not linked"));

        // Unlink provider
        oauthProviderService.unlinkProvider(user.getId(), provider);

        // Update user's OAuth providers set
        if (user.getOauthProviders() != null) {
            user.getOauthProviders().remove(provider.name());
            userRepository.save(user);
        }

        // Publish event
        oauthEventPublisher.publishAccountUnlinkedEvent(
                user.getId(), user.getUsername(), user.getEmail(), provider);

        log.info("Unlinked {} account from user: {}", provider, username);
    }

    /**
     * Get list of linked providers for user
     */
    public LinkedAccountsResponse getLinkedAccounts(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new OAuthProviderException("User not found"));

        List<LinkedProviderResponse> providers = oauthProviderService.getLinkedProviders(user.getId());

        return LinkedAccountsResponse.builder()
                .linkedProviders(providers)
                .hasPassword(user.getPassword() != null)
                .canUnlinkAll(user.getPassword() != null || providers.size() > 1)
                .build();
    }

    /**
     * Get user's authentication status
     */
    public AuthStatusResponse getAuthStatus(String username) {
        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new NotFoundException("User not found with username: " + username));

            List<LinkedProviderResponse> providers = oauthProviderService.getLinkedProviders(user.getId());

            boolean hasPassword = user.getPassword() != null && !user.getPassword().isEmpty();
            int oauthCount = providers.size();
            int totalAuthMethods = (hasPassword ? 1 : 0) + oauthCount;
            boolean canUnlinkOAuth = hasPassword || oauthCount > 1;

            String message;
            if (!hasPassword && oauthCount == 1) {
                message = "You only have 1 OAuth provider and no password. Please set a password using /set-password before unlinking.";
            } else if (!hasPassword && oauthCount > 1) {
                message = "You can unlink OAuth providers, but keep at least one authentication method.";
            } else if (hasPassword && oauthCount == 0) {
                message = "You're using traditional login with password only.";
            } else {
                message = "You have multiple authentication methods. You can safely unlink OAuth providers.";
            }

            return AuthStatusResponse.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .hasPassword(hasPassword)
                    .oauthProviders(providers.stream()
                            .map(LinkedProviderResponse::getProvider)
                            .toList())
                    .totalAuthMethods(totalAuthMethods)
                    .canUnlinkOAuth(canUnlinkOAuth)
                    .message(message)
                    .build();

        } catch (NotFoundException e) {
            log.error("User not found: {}", username);
            throw e;
        } catch (Exception e) {
            log.error("Failed to get auth status for user {}: {}", username, e.getMessage(), e);
            throw new OAuthProviderException("Failed to retrieve authentication status", e);
        }
    }

    /**
     * Find or create user from OAuth user info
     */
    private User findOrCreateUser(Provider provider, OAuthCallbackResponse.UserInfo userInfo) {
        // Try to find existing OAuth provider
        Optional<OAuthProvider> existingProvider = oauthProviderService
                .findByProviderAndProviderId(provider, userInfo.getProviderId());

        if (existingProvider.isPresent()) {
            // User exists, return user
            String userId = existingProvider.get().getUserId();
            return userRepository.findById(userId)
                    .orElseThrow(() -> new OAuthProviderException("User not found"));
        }

        // Try to find by email
        Optional<User> existingUser = userRepository.findByEmail(userInfo.getEmail());
        if (existingUser.isPresent()) {
            // User with same email exists, link OAuth account
            return existingUser.get();
        }

        String newPasswordHash = encoder.encode("password@123");

        // Create new user
        User newUser = new User();
        newUser.setUsername(generateUsername(userInfo.getEmail()));
        newUser.setEmail(userInfo.getEmail());
        newUser.setPassword(newPasswordHash); // Placeholder password
        newUser.setFullName(userInfo.getName());
        newUser.setRoles(Set.of(UserRole.CANDIDATE.name())); // OAuth users default to CANDIDATE role
        newUser.setStatus(UserStatus.ACTIVE); // OAuth users are auto-verified
        newUser.setOauthProviders(new HashSet<>(Collections.singletonList(provider.name())));
        newUser.setCreatedAt(Instant.now());
        newUser.setUpdatedAt(Instant.now());

        User savedUser = userRepository.save(newUser);

        // Publish event to user-service to create profile
        publishUserRegistrationEvent(savedUser, provider);

        log.info("Created new user from OAuth: {} via {}", savedUser.getUsername(), provider);
        return savedUser;
    }

    /**
     * Publish user registration event to sync with user-service
     */
    private void publishUserRegistrationEvent(User user, Provider provider) {
        try {
            UserRegistrationEvent event = UserRegistrationEvent.builder()
                    .eventType(EventType.USER_REGISTERED.name())
                    .timestamp(Instant.now())
                    .userData(UserRegistrationEvent.UserData.builder()
                            .userId(user.getId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .fullName(user.getFullName())
                            .role(user.getRoles() != null && !user.getRoles().isEmpty()
                                    ? user.getRoles().iterator().next()
                                    : "CANDIDATE")
                            .status(user.getStatus().name())
                            .build())
                    .build();

            userRegistrationProducer.publishUserRegistrationEvent(event);
            log.info("Published user registration event for OAuth user: {}", user.getUsername());

        } catch (Exception e) {
            log.error("Failed to publish user registration event for OAuth user: {}", user.getUsername(), e);
            // Don't fail the registration if Kafka fails, user is already created in auth
            // DB
        }
    }

    /**
     * Save or update OAuth provider connection
     */
    private void saveOAuthProvider(User user, Provider provider, OAuthCallbackResponse response,
            OAuthCallbackResponse.UserInfo userInfo) {
        Optional<OAuthProvider> existing = oauthProviderService
                .findByProviderAndProviderId(provider, userInfo.getProviderId());

        OAuthProvider oauthProvider;
        if (existing.isPresent()) {
            // Update existing
            oauthProvider = existing.get();
            oauthProvider.setAccessToken(response.getToken());
            oauthProvider.setRefreshToken(response.getRefreshToken());
            oauthProvider.setLastUsedAt(Instant.now());
            if (response.getExpiresIn() != null) {
                oauthProvider.setTokenExpiry(Instant.now().plusSeconds(response.getExpiresIn()));
            }
        } else {
            // Create new
            oauthProvider = buildOAuthProvider(user, provider, response, userInfo);
        }

        oauthProviderService.saveProvider(oauthProvider);
    }

    /**
     * Build OAuth provider entity
     */
    private OAuthProvider buildOAuthProvider(User user, Provider provider, OAuthCallbackResponse response,
            OAuthCallbackResponse.UserInfo userInfo) {
        OAuthProvider oauthProvider = new OAuthProvider();
        oauthProvider.setUserId(user.getId());
        oauthProvider.setProvider(provider);
        oauthProvider.setProviderId(userInfo.getProviderId());
        oauthProvider.setEmail(userInfo.getEmail());
        oauthProvider.setDisplayName(userInfo.getName());
        oauthProvider.setProfilePicture(userInfo.getPicture());
        oauthProvider.setAccessToken(response.getToken());
        oauthProvider.setRefreshToken(response.getRefreshToken());
        if (response.getExpiresIn() != null) {
            oauthProvider.setTokenExpiry(Instant.now().plusSeconds(response.getExpiresIn()));
        }
        oauthProvider.setMetadata(Map.of(
                "emailVerified", userInfo.getEmailVerified() != null ? userInfo.getEmailVerified().toString() : "false",
                "locale", userInfo.getLocale() != null ? userInfo.getLocale() : ""));
        oauthProvider.setCreatedAt(Instant.now());
        oauthProvider.setLastUsedAt(Instant.now());
        return oauthProvider;
    }

    /**
     * Update user's last OAuth login info
     */
    private void updateLastOAuthLogin(User user, Provider provider, HttpServletRequest request) {
        User.LastOAuthLogin lastOAuthLogin = new User.LastOAuthLogin();
        lastOAuthLogin.setProvider(provider.name());
        lastOAuthLogin.setTimestamp(Instant.now());
        lastOAuthLogin.setIpAddress(IpAddressUtil.getClientIp(request));
        lastOAuthLogin.setDeviceInfo(request.getHeader("User-Agent"));

        user.setLastOAuthLogin(lastOAuthLogin);
        userRepository.save(user);
    }

    /**
     * Generate username from email
     */
    private String generateUsername(String email) {
        String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9]", "");
        String username = baseUsername;
        int counter = 1;

        while (userRepository.existsByUsername(username)) {
            username = baseUsername + counter++;
        }

        return username;
    }

    /**
     * Generate CSRF state token with optional userId for LINK mode
     */
    private String generateState(Provider provider, String clientState, String userId) {
        String randomState = UUID.randomUUID().toString();
        String mode = userId != null ? "LINK" : "LOGIN";
        String userContext = userId != null ? userId : "";
        return provider.name() + ":" + randomState + ":" + mode + ":" + userContext + ":"
                + (clientState != null ? clientState : "");
    }

    /**
     * Store state in Redis for CSRF protection
     */
    private void storeState(String state, String redirectUri) {
        String key = OAUTH_STATE_PREFIX + state;
        // Store redirectUri (userId already embedded in state)
        redisTemplate.opsForValue().set(key, redirectUri != null ? redirectUri : "",
                STATE_EXPIRATION_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Validate and consume CSRF state
     * 
     * @return OAuthStateInfo containing redirectUri, mode, and userId
     */
    private OAuthStateInfo validateState(String state) {
        String key = OAUTH_STATE_PREFIX + state;
        String redirectUri = redisTemplate.opsForValue().get(key);

        if (redirectUri == null) {
            throw new InvalidOAuthStateException("Invalid or expired OAuth state");
        }

        // Delete state after validation (one-time use)
        redisTemplate.delete(key);

        // Parse state: PROVIDER:uuid:MODE:userId:clientState
        String[] parts = state.split(":", 5);
        String mode = parts.length > 2 ? parts[2] : "LOGIN";
        String userId = parts.length > 3 && !parts[3].isEmpty() ? parts[3] : null;

        return new OAuthStateInfo(redirectUri.isEmpty() ? null : redirectUri, mode, userId);
    }

    /**
     * Inner class to hold OAuth state information
     */
    private record OAuthStateInfo(String redirectUri, String mode, String userId) {
    }

    /**
     * Get provider service implementation
     */
    private IOAuthProviderService getProviderService(Provider provider) {
        return switch (provider) {
            case GOOGLE -> googleOAuthService;
            case GITHUB -> gitHubOAuthService;
            default -> throw new OAuthProviderException("Unsupported OAuth provider: " + provider);
        };
    }

    /**
     * Get backend base URL for OAuth provider callback
     */
    private String getBackendBaseUrl() {
        return backendBaseUrl;
    }

    /**
     * Get frontend base URL for redirecting user after OAuth success
     */
    private String getFrontendBaseUrl() {
        return frontendBaseUrl;
    }
}
