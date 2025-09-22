package org.workfitai.jobservice.model.dto.request;

import jakarta.validation.constraints.*;
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
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReqJobDTO {

    @NotBlank(message = "Title must not be null")
    @Size(min = 5, max = 120, message = "Title must be between 5 and 120 characters")
    private String title;

    @NotBlank(message = "Description must not be null")
    @Size(min = 20, max = 5000, message = "Description must be between 20 and 5000 characters")
    private String description;

    @NotNull(message = "Employment type must not be null")
    private EmploymentType employmentType;

    @NotNull(message = "Experience level must not be null")
    private ExperienceLevel experienceLevel;

    @NotNull(message = "salaryMin must not be null")
    @DecimalMin(value = "0.0", inclusive = true, message = "salaryMin must be >= 0")
    private BigDecimal salaryMin;

    @NotNull(message = "salaryMax must not be null")
    @DecimalMin(value = "0.0", inclusive = true, message = "salaryMax must be >= 0")
    private BigDecimal salaryMax;

    @NotBlank(message = "Currency must not be null")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code (e.g. USD, VND)")
    private String currency;

    @NotBlank(message = "Location must not be null")
    @Size(min = 2, max = 255, message = "Location must be between 2 and 255 characters")
    private String location;

    @NotNull(message = "Quantity must not be null")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "ExpiresAt must not be null")
    @Future(message = "ExpiresAt must be a future date")
    private Instant expiresAt;

    private JobStatus status = JobStatus.DRAFT;

    @NotBlank(message = "Education level must not be null")
    @Size(min = 2, max = 120, message = "Education level must be between 2 and 120 characters")
    private String educationLevel;

    @NotNull(message = "CompanyId must not be null")
    private String companyNo;

    @NotEmpty(message = "Job must have at least one skill")
    private List<UUID> skillIds;

    @AssertTrue(message = "salaryMax must be greater than or equal to salaryMin")
    public boolean isSalaryValid() {
        if (salaryMin == null || salaryMax == null) return true;
        return salaryMax.compareTo(salaryMin) >= 0;
    }
}
