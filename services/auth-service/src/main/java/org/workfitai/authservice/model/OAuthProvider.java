package org.workfitai.authservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.workfitai.authservice.enums.Provider;

import java.time.Instant;
import java.util.Map;

/**
 * OAuth Provider entity for storing linked OAuth accounts.
 * Supports Google and GitHub authentication.
 */
@Document(collection = "oauth_providers")
@CompoundIndexes({
        @CompoundIndex(name = "provider_providerId_unique", def = "{'provider': 1, 'providerId': 1}", unique = true),
        @CompoundIndex(name = "provider_email", def = "{'provider': 1, 'email': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthProvider {

    @Id
    private String id;

    /**
     * User ID from auth.users collection
     */
    @Indexed
    private String userId;

    /**
     * OAuth provider name: GOOGLE, GITHUB
     */
    @Indexed
    private Provider provider;

    /**
     * Unique ID from OAuth provider (sub claim)
     */
    private String providerId;

    /**
     * Email from OAuth provider
     */
    @Indexed
    private String email;

    /**
     * Display name from OAuth provider
     */
    private String displayName;

    /**
     * Profile picture URL from OAuth provider
     */
    private String profilePicture;

    /**
     * Encrypted access token
     */
    private String accessToken;

    /**
     * Encrypted refresh token (if available)
     */
    private String refreshToken;

    /**
     * Token expiry timestamp
     */
    private Instant tokenExpiry;

    /**
     * Provider-specific metadata (locale, verified_email, etc.)
     */
    private Map<String, Object> metadata;

    /**
     * Last time this provider was used for login
     */
    private Instant lastUsedAt;

    /**
     * When this OAuth account was linked
     */
    private Instant createdAt;

    /**
     * Last update timestamp
     */
    private Instant updatedAt;
}
