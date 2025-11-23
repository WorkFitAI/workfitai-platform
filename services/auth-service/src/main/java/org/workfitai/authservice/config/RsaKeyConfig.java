package org.workfitai.authservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.workfitai.authservice.utils.KeyGenerator;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class RsaKeyConfig {

    private static final Logger logger = LoggerFactory.getLogger(RsaKeyConfig.class);

    @Bean
    public RsaKeyProperties rsaKeyProperties() {
        try {
            // Ensure keys exist
            KeyGenerator.generateKeysIfNotExist();

            // Load keys
            RSAPublicKey publicKey = loadPublicKey();
            RSAPrivateKey privateKey = loadPrivateKey();

            return new RsaKeyProperties(publicKey, privateKey);

        } catch (Exception e) {
            logger.error("Failed to load RSA keys", e);
            throw new RuntimeException("Could not load RSA keys", e);
        }
    }

    private RSAPublicKey loadPublicKey() throws Exception {
        Path keyPath = getKeyPath("public_key.pem");
        String content = Files.readString(keyPath);

        // Remove PEM headers and whitespace
        String publicKeyPEM = content
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);

        logger.info("✅ Loaded RSA public key from: {}", keyPath);
        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }

    private RSAPrivateKey loadPrivateKey() throws Exception {
        Path keyPath = getKeyPath("private_key_pkcs8.pem");
        String content = Files.readString(keyPath);

        // Remove PEM headers and whitespace
        String privateKeyPEM = content
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);

        logger.info("✅ Loaded RSA private key from: {}", keyPath);
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }

    private Path getKeyPath(String filename) {
        // Check if running in Docker container
        String javaClassPath = System.getProperty("java.class.path");
        if (javaClassPath != null && javaClassPath.contains("/app.jar")) {
            return Paths.get("/tmp/auth-keys", filename);
        } else {
            return Paths.get("src", "main", "resources", "keys", filename);
        }
    }
}