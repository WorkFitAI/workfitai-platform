package org.workfitai.monitoringservice.security;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

/**
 * Fetches RSA public key from auth-service for JWT signature verification.
 *
 * Uses RestTemplate (not Feign) since monitoring-service does not have OpenFeign on classpath.
 * Key is cached in memory after first load; refresh possible via refreshKey().
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicKeyProvider {

    private final RestTemplate restTemplate;

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    private RSAPublicKey cachedKey;

    @PostConstruct
    public void init() {
        try {
            this.cachedKey = fetchPublicKey();
            log.info("✅ Public key loaded from auth-service");
        } catch (Exception e) {
            log.warn("⚠️ Failed to load public key on startup: {}. Will retry on first request.", e.getMessage());
        }
    }

    public RSAPublicKey getPublicKey() throws Exception {
        if (cachedKey == null) {
            synchronized (this) {
                if (cachedKey == null) {
                    cachedKey = fetchPublicKey();
                    log.info("✅ Public key loaded (lazy)");
                }
            }
        }
        return cachedKey;
    }

    public void refreshKey() {
        try {
            this.cachedKey = fetchPublicKey();
            log.info("✅ Public key refreshed");
        } catch (Exception e) {
            log.error("❌ Failed to refresh public key: {}", e.getMessage());
            throw new RuntimeException("Failed to refresh public key", e);
        }
    }

    private RSAPublicKey fetchPublicKey() throws Exception {
        String url = authServiceUrl + "/api/v1/keys/public";
        log.debug("Fetching public key from: {}", url);

        Map<String, String> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, String>>() {}
        ).getBody();

        if (response == null || !response.containsKey("publicKey")) {
            throw new IllegalStateException("publicKey not found in auth-service response");
        }

        byte[] decoded = Base64.getDecoder().decode(response.get("publicKey"));
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(decoded));
    }
}
