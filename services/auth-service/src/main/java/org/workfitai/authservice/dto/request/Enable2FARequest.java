package org.workfitai.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Enable2FARequest {

    @NotBlank(message = "Method is required")
    @Pattern(regexp = "TOTP|EMAIL", message = "Method must be TOTP or EMAIL")
    private String method;
}
