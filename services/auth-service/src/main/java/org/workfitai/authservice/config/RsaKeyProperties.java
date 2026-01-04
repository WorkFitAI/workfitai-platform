package org.workfitai.authservice.config;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for RSA keys used in JWT signing and verification
 */
@ConfigurationProperties(prefix = "auth.jwt.rsa")
public record RsaKeyProperties(
        RSAPublicKey publicKey,
        RSAPrivateKey privateKey) {
}