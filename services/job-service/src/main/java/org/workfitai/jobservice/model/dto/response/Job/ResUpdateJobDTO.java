package org.workfitai.jobservice.model.dto.response.Job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
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
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class ResUpdateJobDTO {

    private UUID postId;

    private String title;

    private String shortDescription;

    private String description;

    private String benefits;

    private String requirements;

    private String responsibilities;

    private String requiredExperience;

    private EmploymentType employmentType;

    private ExperienceLevel experienceLevel;

    private BigDecimal salaryMin;

    private BigDecimal salaryMax;

    private String currency;

    private String location;

    private Integer quantity;

    private Instant expiresAt;

    private JobStatus status;

    private String educationLevel;

    private Integer totalApplications;

    private Long views;

    private String companyNo;

    private String companyName;

    @JsonIgnoreProperties("jobs")
    private List<String> skillNames;

    private String bannerUrl;

    private Instant createdDate;

    private Instant lastModifiedDate;
}
