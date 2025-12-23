package org.workfitai.authservice.service.oauth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.authservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.authservice.dto.request.OAuthAuthorizeRequest;
import org.workfitai.authservice.dto.request.OAuthLinkRequest;
import org.workfitai.authservice.dto.response.*;
import org.workfitai.authservice.model.OAuthProvider;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.enums.EventType;
import org.workfitai.authservice.enums.Provider;
import org.workfitai.authservice.enums.UserRole;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.messaging.UserRegistrationProducer;
import org.workfitai.authservice.exception.InvalidOAuthStateException;
import org.workfitai.authservice.exception.OAuthProviderException;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.security.JwtService;
import org.workfitai.authservice.service.oauth.provider.GitHubOAuthService;
import org.workfitai.authservice.service.oauth.provider.GoogleOAuthService;
import org.workfitai.authservice.service.oauth.provider.IOAuthProviderService;
import org.workfitai.authservice.util.IpAddressUtil;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private final JwtService jwtService;
    private final UserRegistrationProducer userRegistrationProducer;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String OAUTH_STATE_PREFIX = "oauth:state:";
    private static final long STATE_EXPIRATION_MINUTES = 10;

    /**
     * Generate authorization URL for OAuth flow
     */
    public OAuthAuthorizeResponse authorize(Provider provider, OAuthAuthorizeRequest request) {
        IOAuthProviderService providerService = getProviderService(provider);

        // Generate and store CSRF state
        String state = generateState(provider, request.getState());
        storeState(state, request.getRedirectUri());

        // Generate authorization URL
        String scope = request.getScope() != null && !request.getScope().isEmpty()
                ? String.join(" ", request.getScope())
                : providerService.getDefaultScope();
        String authUrl = providerService.getAuthorizationUrl(request.getRedirectUri(), state, scope);

        log.info("Generated OAuth authorization URL for provider: {}", provider);

        return OAuthAuthorizeResponse.builder()
                .authorizationUrl(authUrl)
                .state(state)
                .provider(provider.name())
                .expiresIn(STATE_EXPIRATION_MINUTES * 60)
                .build();
    }

    /**
     * Handle OAuth callback and create/login user
     */
    @Transactional
    public OAuthCallbackResponse handleCallback(Provider provider, String code, String state,
            String redirectUri, HttpServletRequest request) {
        // Validate CSRF state and get stored redirectUri
        String storedRedirectUri = validateState(state);
        // Use stored redirectUri if parameter is null
        String finalRedirectUri = redirectUri != null ? redirectUri : storedRedirectUri;

        IOAuthProviderService providerService = getProviderService(provider);

        // Exchange code for tokens and user info
        OAuthCallbackResponse response = providerService.handleCallback(code, finalRedirectUri);
        OAuthCallbackResponse.UserInfo userInfo = response.getUserInfo();

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

        // Publish login event
        String ipAddress = IpAddressUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        oauthEventPublisher.publishLoginEvent(user, provider, ipAddress, userAgent);

        log.info("OAuth callback successful for user: {} via {}", user.getUsername(), provider);

        // Return response with JWT tokens (not OAuth tokens)
        // Access token expiration is configurable, default 15 minutes (900 seconds)
        return OAuthCallbackResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(900L) // 15 minutes default
                .tokenType("Bearer")
                .userInfo(userInfo)
                .build();
    }

    /**
     * Link OAuth provider to existing user account
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

        // Create new user
        User newUser = new User();
        newUser.setUsername(generateUsername(userInfo.getEmail()));
        newUser.setEmail(userInfo.getEmail());
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
     * Generate CSRF state token
     */
    private String generateState(Provider provider, String clientState) {
        String randomState = UUID.randomUUID().toString();
        return provider.name() + ":" + randomState + ":" + (clientState != null ? clientState : "");
    }

    /**
     * Store state in Redis for CSRF protection
     */
    private void storeState(String state, String redirectUri) {
        String key = OAUTH_STATE_PREFIX + state;
        // Store redirectUri so we can retrieve it during callback
        redisTemplate.opsForValue().set(key, redirectUri != null ? redirectUri : "",
                STATE_EXPIRATION_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Validate and consume CSRF state
     * 
     * @return stored redirectUri
     */
    private String validateState(String state) {
        String key = OAUTH_STATE_PREFIX + state;
        String redirectUri = redisTemplate.opsForValue().get(key);

        if (redirectUri == null) {
            throw new InvalidOAuthStateException("Invalid or expired OAuth state");
        }

        // Delete state after validation (one-time use)
        redisTemplate.delete(key);

        return redirectUri.isEmpty() ? null : redirectUri;
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
}
