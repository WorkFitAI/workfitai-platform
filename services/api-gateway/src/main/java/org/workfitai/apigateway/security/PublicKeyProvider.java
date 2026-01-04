package org.workfitai.apigateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.workfitai.apigateway.client.AuthWebClient;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class PublicKeyProvider {

  private final AuthWebClient authWebClient;
  private volatile RSAPublicKey cachedKey; // thread-safe

  // ❌ Không fetch ngay startup nữa — tránh Gateway fail nếu Auth chưa ready
  // ✅ Lazy fetch lần đầu tiên khi cần dùng
  public RSAPublicKey getPublicKey() {
    if (cachedKey == null) {
      synchronized (this) {
        if (cachedKey == null) {
          try {
            log.warn("⚠️ Public key not cached — fetching from Auth Service...");
            cachedKey = fetchPublicKey();
            log.info("✅ Successfully fetched and cached public key.");
          } catch (Exception e) {
            log.error("❌ Failed to fetch public key from Auth Service: {}", e.getMessage());
            throw new IllegalStateException("Could not fetch public key from Auth Service", e);
          }
        }
      }
    }
    return cachedKey;
  }

  private RSAPublicKey fetchPublicKey() throws Exception {
    Map<String, String> response = authWebClient.getPublicKey();
    if (response == null || !response.containsKey("publicKey")) {
      throw new IllegalStateException("Auth Service returned invalid public key response");
    }

    String encoded = response.get("publicKey");
    byte[] decoded = Base64.getDecoder().decode(encoded);
    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
    return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
  }
}
