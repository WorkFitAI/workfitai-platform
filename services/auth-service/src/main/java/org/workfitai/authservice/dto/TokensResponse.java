package org.workfitai.authservice.dto;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class TokensResponse {
    private String accessToken;
    private long expiryInMinutes;
    private String username;
    private Set<String> roles;

    public static TokensResponse of(String access, long expiresMs) {
        return TokensResponse.builder()
                .accessToken(access)
                .expiryInMinutes(expiresMs)
                .build();
    }

    public static TokensResponse withUserInfo(String access, long expiresMs, String username, Set<String> roles) {
        return TokensResponse.builder()
                .accessToken(access)
                .expiryInMinutes(expiresMs)
                .username(username)
                .roles(roles)
                .build();
    }
}
