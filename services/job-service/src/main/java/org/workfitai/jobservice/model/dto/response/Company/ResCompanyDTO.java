package org.workfitai.jobservice.model.dto.response.Company;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResCompanyDTO {
    private String companyNo;
    private String name;
    private String description;
    private String address;
    private String websiteUrl;
    private String logoUrl;
    private String size;
}
