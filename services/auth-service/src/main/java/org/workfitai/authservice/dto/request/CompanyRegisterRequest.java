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
public class CompanyRegisterRequest {

    // @NotBlank(message = "Company ID is required for HR manager registration")
    // @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "Company ID must be a valid
    // UUID")
    // private String companyId;

    @NotBlank(message = "Company name is required")
    private String name;

    private String logoUrl;
    private String websiteUrl;
    private String description;

    @NotBlank(message = "Company address is required")
    private String address;

    private String size;
}
