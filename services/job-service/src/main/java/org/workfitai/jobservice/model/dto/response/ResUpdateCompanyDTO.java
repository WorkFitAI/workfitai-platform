package org.workfitai.jobservice.model.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResUpdateCompanyDTO {

    private String companyNo;
    private String name;
    private String logoUrl;
    private String websiteUrl;
    private String description;
    private String address;
    private String size;

    private String updatedAt;
}