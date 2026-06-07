package org.workfitai.jobservice.model.dto.response.Company;

import lombok.*;

import java.time.Instant;

import org.workfitai.jobservice.model.dto.AuditableResponse;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResUpdateCompanyDTO implements AuditableResponse {

    private String companyNo;
    private String name;
    private String logoUrl;
    private String websiteUrl;
    private String description;
    private String address;
    private String size;
    private Instant lastModifiedDate;

    @JsonIgnore
    @Override
    public String getAuditId() {
        return companyNo;
    }

}