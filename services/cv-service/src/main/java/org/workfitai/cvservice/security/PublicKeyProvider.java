package org.workfitai.cvservice.security;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.workfitai.cvservice.client.AuthFeignClient;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicKeyProvider {

    private final AuthFeignClient authFeignClient;
    private RSAPublicKey cachedKey;

    @PostConstruct
    public void init() {
        try {
            this.cachedKey = fetchPublicKey();
            log.info("Loaded public key from Auth Service");
        } catch (Exception e) {
            log.warn("Failed to load public key on startup, will retry later");
        }
    }

    public RSAPublicKey getPublicKey() throws Exception {
        if (cachedKey == null) {
            cachedKey = fetchPublicKey();
        }
        return cachedKey;
    }

    private RSAPublicKey fetchPublicKey() throws Exception {
        Map<String, String> response = authFeignClient.getPublicKey();
        String encoded = response.get("publicKey");
        byte[] decoded = Base64.getDecoder().decode(encoded);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }
}
