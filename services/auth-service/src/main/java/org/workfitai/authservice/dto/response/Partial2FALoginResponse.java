package org.workfitai.authservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Partial2FALoginResponse {
    private String tempToken; // Temporary token valid for 5 minutes
    private String message;
    private String method; // TOTP or EMAIL
    private String maskedEmail; // For EMAIL method
    private Long expiresIn; // Seconds until temp token expires
    private Boolean require2FA;
}
