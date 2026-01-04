package org.workfitai.authservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for user authentication status
 * Shows which authentication methods user has enabled
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthStatusResponse {

    private String userId;
    private String username;
    private String email;

    /**
     * Whether user has set a password (can login with username/password)
     */
    private boolean hasPassword;

    /**
     * List of linked OAuth providers (GOOGLE, GITHUB, etc.)
     */
    private List<String> oauthProviders;

    /**
     * Total number of authentication methods available
     * (password counts as 1, each OAuth provider counts as 1)
     */
    private int totalAuthMethods;

    /**
     * Whether user can unlink OAuth providers
     * (true if hasPassword OR has multiple OAuth providers)
     */
    private boolean canUnlinkOAuth;

    /**
     * Message for user about their authentication status
     */
    private String message;
}
