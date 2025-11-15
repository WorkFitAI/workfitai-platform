package org.workfitai.apigateway.config;

import java.security.interfaces.RSAPublicKey;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for RSA public key used in JWT verification in API
 * Gateway
 */
@ConfigurationProperties(prefix = "auth.jwt.rsa")
public record RsaKeyProperties(
        RSAPublicKey publicKey) {
}