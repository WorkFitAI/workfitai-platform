package org.workfitai.authservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for all linked OAuth accounts
 */
@Data
@Builder
public class LinkedAccountsResponse {

    private List<LinkedProviderResponse> linkedProviders;

    /**
     * Whether user has set a password (for account security)
     */
    private Boolean hasPassword;

    /**
     * Whether user can unlink all providers (false if no password and only 1
     * provider)
     */
    private Boolean canUnlinkAll;
}
