package org.workfitai.authservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HRProfileRequest {

    @NotBlank(message = "Department is required for HR users")
    private String department;

    /**
     * Email of the HR Manager who manages the company.
     * Required for HR role - they will be assigned to the same company as this HR
     * Manager.
     * Not needed for HR_MANAGER role (they create new company).
     * Validation is done at service level based on role.
     */
    @Email(message = "HR Manager email must be valid")
    private String hrManagerEmail;

    @NotBlank(message = "Address is required for HR users")
    private String address;
}
