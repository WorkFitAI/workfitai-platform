package org.workfitai.applicationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * Feign client for authentication-related endpoints from auth-service.
 * 
 * Used for:
 * - Fetching RSA public key for JWT validation
 * 
 * Service Discovery:
 * - Uses Consul service name "auth"
 * - In Docker: Consul discovers auth-service automatically
 * - In Local: Can override with auth-service.url property
 */
@FeignClient(name = "auth", // Consul service name (same as in docker-compose)
        path = "/api/v1/keys")
public interface AuthServiceClient {

    /**
     * Retrieves the RSA public key for JWT signature verification.
     * 
     * Endpoint: GET /api/v1/keys/public
     * 
     * Response format:
     * {
     *   "alg": "RS256",
     *   "type": "RSA",
     *   "publicKey": "BASE64_ENCODED_PUBLIC_KEY"
     * }
     * 
     * The public key is used to verify JWT signatures across all microservices.
     * Keys are cached by PublicKeyProvider for performance.
     * 
     * @return Map containing the base64-encoded public key
     */
    @GetMapping("/public")
    Map<String, String> getPublicKey();
}
