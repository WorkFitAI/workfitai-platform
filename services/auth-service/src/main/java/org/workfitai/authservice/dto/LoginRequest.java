package org.workfitai.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {
    @NotBlank
    private String usernameOrEmail;
    @NotBlank @Size(min = 8, max = 64)
    private String password;
}