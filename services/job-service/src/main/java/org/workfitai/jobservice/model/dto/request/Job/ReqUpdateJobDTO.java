package org.workfitai.jobservice.model.dto.request.Job;

import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.jobservice.model.enums.EmploymentType;
import org.workfitai.jobservice.model.enums.ExperienceLevel;
import org.workfitai.jobservice.model.enums.JobStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReqUpdateJobDTO {

    @NotNull(message = "JobId must not be null")
    private UUID jobId;

    private String title;

    @Size(min = 5, max = 255, message = "Short description must be between 5 and 255 characters")
    private String shortDescription;

    @Size(min = 20, max = 5000, message = "Description must be between 20 and 5000 characters")
    private String description;

    @Size(max = 5000, message = "Benefits must be <= 5000 characters")
    private String benefits;

    @Size(max = 5000, message = "Requirements must be <= 5000 characters")
    private String requirements;

    @Size(max = 5000, message = "Responsibilities must be <= 5000 characters")
    private String responsibilities;

    @Size(min = 2, max = 120, message = "Required experience must be between 2 and 120 characters")
    private String requiredExperience;

    private EmploymentType employmentType;

    private ExperienceLevel experienceLevel;

    @DecimalMin(value = "0.0", inclusive = true, message = "salaryMin must be >= 0")
    private BigDecimal salaryMin;

    @DecimalMin(value = "0.0", inclusive = true, message = "salaryMax must be >= 0")
    private BigDecimal salaryMax;

    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code (e.g. USD, VND)")
    private String currency;

    @Size(min = 2, max = 255, message = "Location must be between 2 and 255 characters")
    private String location;

    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @Future(message = "ExpiresAt must be a future date")
    private Instant expiresAt;

    @Size(min = 2, max = 120, message = "Education level must be between 2 and 120 characters")
    private String educationLevel;

    @NotNull(message = "CompanyId must not be null")
    private String companyNo;
    
    private List<UUID> skillIds;

    private JobStatus status;

    private MultipartFile bannerFile;
}
