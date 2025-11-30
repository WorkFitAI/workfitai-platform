package org.workfitai.applicationservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing company data from job-service.
 * 
 * Nested inside JobDTO for job details response.
 * Contains only essential fields for display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDTO {

    /**
     * Unique company identifier (UUID format).
     */
    private String companyId;

    /**
     * Company name (e.g., "TechCorp Inc.").
     */
    private String name;

    /**
     * Company logo URL.
     */
    private String logo;

    /**
     * Company location.
     */
    private String address;
}
