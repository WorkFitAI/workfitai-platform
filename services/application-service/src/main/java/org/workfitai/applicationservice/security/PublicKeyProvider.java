package org.workfitai.applicationservice.security;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.client.AuthServiceClient;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

/**
 * Service for providing RSA public key from auth-service.
 * 
 * The public key is used to verify JWT token signatures.
 * This ensures all tokens were actually issued by auth-service.
 * 
 * Caching Strategy:
 * - Loads key on application startup
 * - Caches key in memory for subsequent validations
 * - Can be refreshed if auth-service rotates keys
 * 
 * Startup Behavior:
 * - If auth-service is unavailable at startup, logs warning
 * - Retries on first JWT validation attempt
 * - Fails authentication if key cannot be retrieved
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicKeyProvider {

    private final AuthServiceClient authServiceClient;

    /**
     * Cached RSA public key to avoid repeated Feign calls.
     */
    private RSAPublicKey cachedKey;

    /**
     * Attempts to load public key on application startup.
     * 
     * Non-blocking: If auth-service is down, logs warning and continues.
     * Key will be loaded on first authentication attempt.
     */
    @PostConstruct
    public void init() {
        try {
            this.cachedKey = fetchPublicKey();
            log.info("✅ Successfully loaded public key from auth-service");
        } catch (Exception e) {
            log.warn("⚠️ Failed to load public key on startup: {}. Will retry on first request.",
                    e.getMessage());
        }
    }

    /**
     * Returns the cached public key, fetching from auth-service if not cached.
     * 
     * Thread-safe: Multiple threads may call this, but fetchPublicKey()
     * is idempotent and returning a slightly stale key is acceptable.
     * 
     * @return RSA public key for JWT validation
     * @throws Exception if key cannot be fetched from auth-service
     */
    public RSAPublicKey getPublicKey() throws Exception {
        if (cachedKey == null) {
            synchronized (this) {
                if (cachedKey == null) {
                    cachedKey = fetchPublicKey();
                    log.info("✅ Loaded public key from auth-service (lazy load)");
                }
            }
        }
        return cachedKey;
    }

    /**
     * Forces refresh of cached public key.
     * 
     * Use case: Key rotation in auth-service
     * Can be triggered manually via actuator endpoint if implemented.
     */
    public void refreshKey() {
        try {
            this.cachedKey = fetchPublicKey();
            log.info("✅ Public key refreshed successfully");
        } catch (Exception e) {
            log.error("❌ Failed to refresh public key: {}", e.getMessage());
            throw new RuntimeException("Failed to refresh public key", e);
        }
    }

    /**
     * Fetches and parses public key from auth-service.
     * 
     * Process:
     * 1. Call auth-service /public-key endpoint
     * 2. Extract base64-encoded key from response
     * 3. Decode base64 to bytes
     * 4. Parse X509 format to RSAPublicKey
     * 
     * @return Parsed RSA public key
     * @throws Exception if fetch or parsing fails
     */
    private RSAPublicKey fetchPublicKey() throws Exception {
        log.debug("Fetching public key from auth-service...");

        // Call auth-service to get the public key
        Map<String, String> response = authServiceClient.getPublicKey();
        String encoded = response.get("publicKey");

        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("Public key not found in auth-service response");
        }

        // Decode from Base64
        byte[] decoded = Base64.getDecoder().decode(encoded);

        // Parse as X509 encoded RSA public key
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }
}
