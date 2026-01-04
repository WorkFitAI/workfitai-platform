package org.workfitai.cvservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class SecurityJwtConfiguration {
    private final PublicKeyProvider publicKeyProvider;

    public SecurityJwtConfiguration(PublicKeyProvider publicKeyProvider) {
        this.publicKeyProvider = publicKeyProvider;
    }

    @Bean
    public JwtDecoder jwtDecoder() throws Exception {
        // Lấy public key từ Auth Service
        return NimbusJwtDecoder.withPublicKey(publicKeyProvider.getPublicKey()).build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
