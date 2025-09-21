package org.workfitai.authservice.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssuedTokens {
    private String tokenType;
    private String accessToken;
    private String refreshToken;
    private long expiresIn;

    public static IssuedTokens of(String access, String refresh, long expiresMs) {
        return IssuedTokens.builder()
                .tokenType("Bearer")
                .accessToken(access)
                .refreshToken(refresh)
                .expiresIn(expiresMs)
                .build();
    }
}