package org.workfitai.authservice.service.oauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.authservice.dto.response.LinkedProviderResponse;
import org.workfitai.authservice.enums.Provider;
import org.workfitai.authservice.exception.CannotUnlinkLastAuthMethodException;
import org.workfitai.authservice.exception.ProviderAlreadyLinkedException;
import org.workfitai.authservice.model.OAuthProvider;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.OAuthProviderRepository;
import org.workfitai.authservice.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing OAuth provider connections in database
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthProviderService {

    private final OAuthProviderRepository oauthProviderRepository;
    private final OAuthTokenService oauthTokenService;
    private final UserRepository userRepository;

    /**
     * Save or update OAuth provider connection
     */
    @Transactional
    public OAuthProvider saveProvider(OAuthProvider provider) {
        // Encrypt tokens before saving
        if (provider.getAccessToken() != null) {
            String encrypted = oauthTokenService.encrypt(provider.getAccessToken());
            provider.setAccessToken(encrypted);
        }
        if (provider.getRefreshToken() != null) {
            String encrypted = oauthTokenService.encrypt(provider.getRefreshToken());
            provider.setRefreshToken(encrypted);
        }

        provider.setUpdatedAt(Instant.now());
        return oauthProviderRepository.save(provider);
    }

    /**
     * Find provider by provider type and provider's user ID
     */
    public Optional<OAuthProvider> findByProviderAndProviderId(Provider provider, String providerId) {
        return oauthProviderRepository.findByProviderAndProviderId(provider, providerId)
                .map(this::decryptTokens);
    }

    /**
     * Find all providers linked to a user
     */
    public List<OAuthProvider> findByUserId(String userId) {
        return oauthProviderRepository.findByUserId(userId).stream()
                .map(this::decryptTokens)
                .collect(Collectors.toList());
    }

    /**
     * Check if user has linked a specific provider
     */
    public boolean isProviderLinked(String userId, Provider provider) {
        return oauthProviderRepository.existsByUserIdAndProvider(userId, provider);
    }

    /**
     * Link OAuth provider to existing user account
     *
     * @throws ProviderAlreadyLinkedException if provider already linked
     */
    @Transactional
    public OAuthProvider linkProvider(String userId, OAuthProvider provider) {
        // Check if already linked
        if (isProviderLinked(userId, provider.getProvider())) {
            throw new ProviderAlreadyLinkedException(
                    "This " + provider.getProvider() + " account is already linked to your account");
        }

        provider.setUserId(userId);
        return saveProvider(provider);
    }

    /**
     * Unlink OAuth provider from user account
     *
     * @throws CannotUnlinkLastAuthMethodException if this is the last auth method
     */
    @Transactional
    public void unlinkProvider(String userId, Provider provider) {
        // Get user to check password
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean hasPassword = user.getPassword() != null && !user.getPassword().isEmpty();
        long linkedProviders = oauthProviderRepository.countByUserId(userId);

        log.info("Unlink check - userId: {}, username: {}, provider: {}, hasPassword: {}, linkedProviders: {}",
                userId, user.getUsername(), provider, hasPassword, linkedProviders);

        // Cannot unlink if this is the ONLY auth method (no password and only 1
        // provider)
        if (!hasPassword && linkedProviders <= 1) {
            String errorMsg = String.format(
                    "Cannot unlink %s - this is your only login method. Please set a password first using /set-password endpoint, or link another OAuth provider.",
                    provider);
            log.warn("Unlink blocked for user {}: {}", user.getUsername(), errorMsg);
            throw new CannotUnlinkLastAuthMethodException(errorMsg);
        }

        oauthProviderRepository.deleteByUserIdAndProvider(userId, provider);
        log.info("Successfully unlinked provider {} for user {} (hasPassword={}, remainingProviders={})",
                provider, user.getUsername(), hasPassword, linkedProviders - 1);
    }

    /**
     * Get list of linked providers for user (without sensitive data)
     */
    public List<LinkedProviderResponse> getLinkedProviders(String userId) {
        return oauthProviderRepository.findByUserId(userId).stream()
                .map(p -> LinkedProviderResponse.builder()
                        .provider(p.getProvider().name())
                        .email(p.getEmail())
                        .displayName(p.getDisplayName())
                        .profilePicture(p.getProfilePicture())
                        .linkedAt(p.getCreatedAt())
                        .lastUsed(p.getLastUsedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Update last used timestamp
     */
    @Transactional
    public void updateLastUsed(String userId, Provider provider) {
        oauthProviderRepository.findByUserId(userId).stream()
                .filter(p -> p.getProvider() == provider)
                .findFirst()
                .ifPresent(p -> {
                    p.setLastUsedAt(Instant.now());
                    oauthProviderRepository.save(p);
                });
    }

    /**
     * Refresh OAuth tokens
     */
    @Transactional
    public void refreshTokens(String userId, Provider provider, String newAccessToken,
            String newRefreshToken, Long expiresIn) {
        oauthProviderRepository.findByUserId(userId).stream()
                .filter(p -> p.getProvider() == provider)
                .findFirst()
                .ifPresent(p -> {
                    p.setAccessToken(oauthTokenService.encrypt(newAccessToken));
                    if (newRefreshToken != null) {
                        p.setRefreshToken(oauthTokenService.encrypt(newRefreshToken));
                    }
                    if (expiresIn != null) {
                        p.setTokenExpiry(Instant.now().plusSeconds(expiresIn));
                    }
                    p.setUpdatedAt(Instant.now());
                    oauthProviderRepository.save(p);
                });
    }

    /**
     * Decrypt tokens in provider entity
     */
    private OAuthProvider decryptTokens(OAuthProvider provider) {
        if (provider.getAccessToken() != null) {
            provider.setAccessToken(oauthTokenService.decrypt(provider.getAccessToken()));
        }
        if (provider.getRefreshToken() != null) {
            provider.setRefreshToken(oauthTokenService.decrypt(provider.getRefreshToken()));
        }
        return provider;
    }

    /**
     * Delete all providers for a user (for account deletion)
     */
    @Transactional
    public void deleteAllByUserId(String userId) {
        oauthProviderRepository.findByUserId(userId).forEach(oauthProviderRepository::delete);
        log.info("Deleted all OAuth providers for user {}", userId);
    }
}
