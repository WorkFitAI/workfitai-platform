package org.workfitai.authservice.dto.response;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeResponse {
    private boolean authenticated;
    private String username;
    private Set<String> roles;
    private String companyId;

    public static MeResponse unauthenticated() {
        return MeResponse.builder()
                .authenticated(false)
                .build();
    }

    public static MeResponse authenticated(String username, Set<String> roles, String companyId) {
        return MeResponse.builder()
                .authenticated(true)
                .username(username)
                .roles(roles)
                .companyId(companyId)
                .build();
    }
}
