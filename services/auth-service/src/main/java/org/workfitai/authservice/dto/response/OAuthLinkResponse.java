package org.workfitai.authservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response DTO after successfully linking an OAuth account
 */
@Data
@Builder
public class OAuthLinkResponse {

    private Boolean success;

    private String provider;

    private String email;

    private String displayName;

    private Instant linkedAt;
}
