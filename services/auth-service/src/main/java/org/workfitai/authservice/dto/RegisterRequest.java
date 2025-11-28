package org.workfitai.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.authservice.constants.Messages;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {
    @NotBlank(message = Messages.Validation.USERNAME_REQUIRED)
    private String username;

    @NotBlank(message = Messages.Validation.EMAIL_REQUIRED)
    @Email(message = Messages.Validation.EMAIL_INVALID)
    private String email;

    @NotBlank(message = Messages.Validation.PASSWORD_REQUIRED)
    @Size(min = 8, max = 128, message = Messages.Validation.PASSWORD_LENGTH)
    private String password;

    // ===== Required fields from UserEntity =====
    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 255, message = "Full name must be between 3-255 characters")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(\\+84)?\\d{10}$", message = "Phone number must be 10 digits or start with +84")
    private String phoneNumber;
}
