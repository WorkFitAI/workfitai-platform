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

    @NotBlank(message = "Company tax ID (companyNo) is required for HR Manager registration")
    private String companyNo; // Mã số thuế - will be primary key in job-service

    @NotBlank(message = "Company name is required")
    private String name;

    private String logoUrl;
    private String websiteUrl;
    private String description;

    @NotBlank(message = "Company address is required")
    private String address;

    private String size;
}
