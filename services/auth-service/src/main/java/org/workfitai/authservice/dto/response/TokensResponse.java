package org.workfitai.authservice.dto.response;

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
    private long expiryInMs;
    private String username;
    private Set<String> roles;
    private String companyId;

    public static TokensResponse of(String access, long expiresMs) {
        return TokensResponse.builder()
                .accessToken(access)
                .expiryInMs(expiresMs)
                .build();
    }

    public static TokensResponse withUserInfo(String access, long expiresMs, String username, Set<String> roles) {
        return TokensResponse.builder()
                .accessToken(access)
                .expiryInMs(expiresMs)
                .username(username)
                .roles(roles)
                .build();
    }

    public static TokensResponse withUserInfo(String access, long expiresMs, String username, Set<String> roles, String companyId) {
        return TokensResponse.builder()
                .accessToken(access)
                .expiryInMs(expiresMs)
                .username(username)
                .roles(roles)
                .companyId(companyId)
                .build();
    }
}
