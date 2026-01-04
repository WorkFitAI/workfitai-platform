package org.workfitai.authservice.service.oauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting and decrypting OAuth tokens using AES-256-GCM.
 * Tokens are encrypted before storing in database for security.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthTokenService {

    @Value("${app.oauth2.token-encryption-key}")
    private String encryptionKeyBase64;

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits

    /**
     * Encrypt OAuth token using AES-256-GCM
     * Format: [IV (12 bytes)][Encrypted data + Auth tag]
     *
     * @param token Plain text token from OAuth provider
     * @return Base64 encoded encrypted token
     */
    public String encrypt(String token) {
        try {
            if (token == null || token.isEmpty()) {
                return null;
            }

            // Generate random IV (Initialization Vector)
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // Prepare cipher
            SecretKey secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            // Encrypt token
            byte[] encryptedBytes = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));

            // Combine IV + encrypted data
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);

            // Encode to Base64 for storage
            return Base64.getEncoder().encodeToString(byteBuffer.array());

        } catch (Exception e) {
            log.error("Failed to encrypt OAuth token", e);
            throw new RuntimeException("Token encryption failed", e);
        }
    }

    /**
     * Decrypt OAuth token using AES-256-GCM
     *
     * @param encryptedToken Base64 encoded encrypted token
     * @return Plain text token
     */
    public String decrypt(String encryptedToken) {
        try {
            if (encryptedToken == null || encryptedToken.isEmpty()) {
                return null;
            }

            // Decode from Base64
            byte[] decoded = Base64.getDecoder().decode(encryptedToken);

            // Extract IV and encrypted data
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] encryptedBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedBytes);

            // Prepare cipher
            SecretKey secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            // Decrypt token
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Failed to decrypt OAuth token", e);
            throw new RuntimeException("Token decryption failed", e);
        }
    }

    /**
     * Get secret key from Base64 encoded string
     */
    private SecretKey getSecretKey() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            log.error("Invalid encryption key format", e);
            throw new RuntimeException("Invalid encryption key", e);
        }
    }

    /**
     * Validate if encryption key is properly configured
     */
    public boolean isEncryptionKeyValid() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
            return keyBytes.length == 32; // 256 bits = 32 bytes
        } catch (Exception e) {
            return false;
        }
    }
}
