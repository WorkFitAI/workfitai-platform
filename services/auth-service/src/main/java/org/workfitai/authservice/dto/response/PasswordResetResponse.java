package org.workfitai.authservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetResponse {
    private String message;
    private String token;
    private LocalDateTime expiresAt;
    private String email;
}
