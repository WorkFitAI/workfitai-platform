package org.workfitai.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO to initiate OAuth authorization flow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthAuthorizeRequest {

    @NotBlank(message = "Redirect URI is required")
    @Pattern(regexp = "^https?://.*", message = "Invalid redirect URI")
    private String redirectUri;

    @NotBlank(message = "State is required for CSRF protection")
    private String state;

    /**
     * Optional: Custom scopes (if not specified, uses provider defaults)
     */
    private List<String> scope;
}
