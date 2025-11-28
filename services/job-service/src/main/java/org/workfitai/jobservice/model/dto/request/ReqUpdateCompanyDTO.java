package org.workfitai.jobservice.model.dto.request;

import jakarta.validation.constraints.NotBlank;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReqUpdateCompanyDTO {

    @NotBlank(message = "company no must not blank")
    private String companyNo;

    private String name;

    private String logoUrl;
    private String websiteUrl;
    private String description;
    private String address;
    private String size;
}
