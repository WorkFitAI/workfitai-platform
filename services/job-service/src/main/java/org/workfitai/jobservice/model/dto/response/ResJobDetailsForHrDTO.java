package org.workfitai.jobservice.model.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
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
public class ResJobDetailsForHrDTO {
    private UUID postId;
    private String title;
    private String description;
    private EmploymentType employmentType;
    private ExperienceLevel experienceLevel;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String currency;
    private Instant expiresAt;
    private JobStatus status;
    private String educationLevel;
    @JsonIgnoreProperties(value = {"jobs"})
    private List<String> skillNames;
    private ResCompanyDTO company;
    private Instant createdDate;
    private Integer quantity;
    private Integer totalApplications;
}
