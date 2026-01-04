package org.workfitai.jobservice.model.dto.response.Company;

import lombok.*;

import java.time.Instant;

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
    private Instant lastModifiedDate;

}