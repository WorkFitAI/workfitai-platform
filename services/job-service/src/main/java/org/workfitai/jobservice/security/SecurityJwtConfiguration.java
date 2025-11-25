package org.workfitai.jobservice.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@RequiredArgsConstructor
public class SecurityJwtConfiguration {

    private final PublicKeyProvider publicKeyProvider;

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
