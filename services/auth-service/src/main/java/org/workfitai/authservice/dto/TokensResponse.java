package org.workfitai.authservice.dto;

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

    public static TokensResponse of(String access, long expiresMs) {
        return TokensResponse.builder()
                .accessToken(access)
                .expiryInMinutes(expiresMs)
                .build();
    }
}
