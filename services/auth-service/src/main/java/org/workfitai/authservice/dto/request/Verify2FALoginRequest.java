package org.workfitai.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Verify2FALoginRequest {
    @NotBlank(message = "Temporary token is required")
    private String tempToken;

    @NotBlank(message = "2FA code is required")
    private String code; // 6-digit TOTP code, email code, or 8-digit backup code

    private Boolean useBackupCode; // true if using backup code instead of regular 2FA code
}
