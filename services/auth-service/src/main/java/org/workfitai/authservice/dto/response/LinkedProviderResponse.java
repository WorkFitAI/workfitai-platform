package org.workfitai.authservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response DTO for a linked OAuth provider
 */
@Data
@Builder
public class LinkedProviderResponse {

    private String provider; // GOOGLE | GITHUB

    private String email;

    private String displayName;

    private String profilePicture;

    private Instant linkedAt;

    private Instant lastUsed;
}
