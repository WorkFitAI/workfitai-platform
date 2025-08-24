package org.workfitai.jobservice.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.workfitai.jobservice.domain.enums.EmploymentType;
import org.workfitai.jobservice.domain.enums.ExperienceLevel;
import org.workfitai.jobservice.domain.enums.JobStatus;

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
    private UUID jobId;

    @NotBlank(message = "Title must not null")
    @Size(min = 5, max = 120)
    private String title;

    @NotBlank(message = "Description must not null")
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    private ExperienceLevel experienceLevel;

    @DecimalMin(value = "0.0", inclusive = true, message = "salaryMin >= 0")
    private BigDecimal salaryMin;

    @DecimalMin(value = "0.0", inclusive = true, message = "salaryMax >= 0")
    private BigDecimal salaryMax;

    private String currency;

    private String location;

    private Integer quantity;

    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    private String educationLevel;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToMany(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"jobs"}) // Trả ra biến skills không chứa biến jobs
    @JoinTable(name = "job_skill", joinColumns = @JoinColumn(name = "job_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private List<Skill> skills;

    @PrePersist
    public void handleBeforeCreate() {
        if (this.jobId == null) {
            this.jobId = UUID.randomUUID();
        }

        if (this.getCreatedBy() == null) {
            this.setCreatedBy("SYSTEM");
        }
    }

    @PreUpdate
    public void handleBeforeUpdate() {
        if (this.getLastModifiedBy() == null) {
            this.setLastModifiedBy("SYSTEM_UPDATED");
        }
    }

    @Override
    public UUID getId() {
        return this.jobId;
    }

    @AssertTrue(message = "salaryMax must greater than salaryMin")
    public boolean isSalaryValid() {
        if (salaryMin == null || salaryMax == null) return true;
        return salaryMax.compareTo(salaryMin) >= 0;
    }
}
