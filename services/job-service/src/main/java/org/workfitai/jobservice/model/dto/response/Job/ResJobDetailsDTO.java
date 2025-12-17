package org.workfitai.jobservice.model.dto.response.Job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.workfitai.jobservice.model.dto.response.Company.ResCompanyDTO;
import org.workfitai.jobservice.model.enums.EmploymentType;
import org.workfitai.jobservice.model.enums.ExperienceLevel;
import org.workfitai.jobservice.model.enums.JobStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
public class ResJobDetailsDTO {

    private UUID postId;
    // Basic info
    private String title;
    private String shortDescription;
    private String description;
    // Job attributes
    private EmploymentType employmentType;
    private ExperienceLevel experienceLevel;
    private String educationLevel;
    private String requiredExperience;
    // Salary
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String currency;
    // Location / Quantity / Metrics
    private String location;
    private Integer quantity;
    private Integer totalApplications;
    // Dates
    private Instant createdDate;
    private Instant lastModifiedDate;
    private Instant expiresAt;
    // Status
    private JobStatus status;
    @JsonIgnoreProperties(value = {"jobs"})
    private List<String> skillNames;
    // Company info
    private ResCompanyDTO company;

    private String bannerUrl;

    private String benefits;

    private String requirements;

    private String responsibilities;

    private String createdBy; // Username of HR who created the job
}
