package org.workfitai.jobservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.workfitai.jobservice.model.enums.EmploymentType;
import org.workfitai.jobservice.model.enums.ExperienceLevel;
import org.workfitai.jobservice.model.enums.JobStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "job_id", updatable = false, nullable = false)
    private UUID jobId;

    @NotBlank(message = "Title must not null")
    @Size(min = 5, max = 120, message = "Title must be between 5 and 120 characters")
    private String title;

    @NotBlank(message = "Description must not null")
    @Size(min = 20, max = 5000, message = "Description must be between 20 and 5000 characters")
    @Column(columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "Employment type must not be null")
    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType;

    @NotNull(message = "Experience level must not be null")
    @Enumerated(EnumType.STRING)
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

    @NotNull(message = "Job status must not be null")
    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @Size(min = 2, max = 120, message = "Education level must be between 2 and 120 characters")
    private String educationLevel;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @NotNull(message = "Company must not be null")
    private Company company;

    @ManyToMany(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"jobs"})
    @NotEmpty(message = "Job must have at least one skill")
    @JoinTable(
            name = "job_skill",
            joinColumns = @JoinColumn(name = "job_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private List<Skill> skills;

    @Override
    public UUID getId() {
        return this.jobId;
    }

    @AssertTrue(message = "salaryMax must be greater than or equal to salaryMin")
    public boolean isSalaryValid() {
        if (salaryMin == null || salaryMax == null) return true;
        return salaryMax.compareTo(salaryMin) >= 0;
    }
}
