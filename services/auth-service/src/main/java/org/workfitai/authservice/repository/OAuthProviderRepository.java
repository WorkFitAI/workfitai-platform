package org.workfitai.authservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.authservice.model.OAuthProvider;
import org.workfitai.authservice.enums.Provider;

import java.util.List;
import java.util.Optional;

/**
 * Repository for OAuthProvider entity
 */
@Repository
public interface OAuthProviderRepository extends MongoRepository<OAuthProvider, String> {

    /**
     * Find OAuth provider by provider type and providerId (unique)
     */
    Optional<OAuthProvider> findByProviderAndProviderId(Provider provider, String providerId);

    /**
     * Find OAuth provider by provider type and email
     */
    Optional<OAuthProvider> findByProviderAndEmail(Provider provider, String email);

    /**
     * Find all OAuth providers for a user
     */
    List<OAuthProvider> findByUserId(String userId);

    /**
     * Find specific provider for a user
     */
    Optional<OAuthProvider> findByUserIdAndProvider(String userId, Provider provider);

    /**
     * Check if provider exists for a user
     */
    boolean existsByUserIdAndProvider(String userId, Provider provider);

    /**
     * Delete OAuth provider for a user
     */
    void deleteByUserIdAndProvider(String userId, Provider provider);

    /**
     * Count OAuth providers for a user
     */
    long countByUserId(String userId);
}
