package org.workfitai.authservice.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO to initiate OAuth authorization flow
 * All fields are optional - backend will generate defaults if not provided
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthAuthorizeRequest {

    /**
     * Optional: Redirect URI (if not specified, uses configured default)
     */
    @Pattern(regexp = "^https?://.*", message = "Invalid redirect URI format")
    private String redirectUri;

    /**
     * Optional: State for CSRF protection (if not specified, backend generates
     * random UUID)
     */
    private String state;

    /**
     * Optional: Custom scopes (if not specified, uses provider defaults)
     */
    private List<String> scope;
}
