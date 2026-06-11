package org.workfitai.jobservice.model.dto.response.Company;

import org.workfitai.jobservice.model.dto.AuditableResponse;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResCompanyDTO implements AuditableResponse {
    private String companyNo;
    private String name;
    private String description;
    private String address;
    private String websiteUrl;
    private String logoUrl;
    private String size;

    @JsonIgnore
    @Override
    public String getAuditId() {
        return companyNo;
    }
}
