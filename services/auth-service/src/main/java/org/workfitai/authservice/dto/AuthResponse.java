package org.workfitai.authservice.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String tokenType;
    private String accessToken;
    private String refreshToken;
    private long expiresIn;

    public static AuthResponse of(String access, String refresh, long expiresMs) {
        return AuthResponse.builder()
                .tokenType("Bearer")
                .accessToken(access)
                .refreshToken(refresh)
                .expiresIn(expiresMs)
                .build();
    }
}