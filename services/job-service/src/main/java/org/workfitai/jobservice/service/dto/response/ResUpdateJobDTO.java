package org.workfitai.jobservice.service.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.workfitai.jobservice.domain.enums.EmploymentType;
import org.workfitai.jobservice.domain.enums.ExperienceLevel;
import org.workfitai.jobservice.domain.enums.JobStatus;

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
    private Instant lastModifiedDate;
    private String lastModifiedBy;
}
