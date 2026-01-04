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
public class Disable2FARequest {

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "2FA code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "2FA code must be 6 digits")
    private String code;
}
