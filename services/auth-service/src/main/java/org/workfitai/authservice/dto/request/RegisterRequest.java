package org.workfitai.authservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.authservice.constants.Messages;
import org.workfitai.authservice.enums.UserRole;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {
    // Username is auto-generated from email (part before @)
    // No longer required from user input

    @NotBlank(message = Messages.Validation.EMAIL_REQUIRED)
    @Email(message = Messages.Validation.EMAIL_INVALID)
    private String email;

    @NotBlank(message = Messages.Validation.PASSWORD_REQUIRED)
    @Size(min = 8, max = 128, message = Messages.Validation.PASSWORD_LENGTH)
    private String password;

    @NotNull(message = "Role is required")
    private UserRole role;

    // ===== Required fields from UserEntity =====
    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 255, message = "Full name must be between 3-255 characters")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(\\+84)?\\d{10}$", message = "Phone number must be 10 digits or start with +84")
    private String phoneNumber;

    // Optional HR profile for HR/HR_MANAGER registration
    @Valid
    private HRProfileRequest hrProfile;

    // Optional company data for HR_MANAGER registration
    @Valid
    private CompanyRegisterRequest company;
}
