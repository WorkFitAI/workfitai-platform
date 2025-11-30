package org.workfitai.authservice.dto;

import java.util.Set;

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
    private String username;
    private Set<String> roles;

    public static IssuedTokens of(String access, String refresh, long expiresMs) {
        return IssuedTokens.builder()
                .tokenType("Bearer")
                .accessToken(access)
                .refreshToken(refresh)
                .expiresIn(expiresMs)
                .build();
    }

    public static IssuedTokens of(String access, String refresh, long expiresMs, String username,
            Set<String> roles) {
        return IssuedTokens.builder()
                .tokenType("Bearer")
                .accessToken(access)
                .refreshToken(refresh)
                .expiresIn(expiresMs)
                .username(username)
                .roles(roles)
                .build();
    }
}
