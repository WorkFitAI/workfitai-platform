package org.workfitai.jobservice.model.dto.response.Report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResJobReportSnapshot {

  private UUID snapshotId;

  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  private String shortDescription;

  private String location;

  private String currency;

  @DecimalMin(value = "0.0", inclusive = true, message = "salaryMin must be >= 0")
  private BigDecimal salaryMin;

  @DecimalMin(value = "0.0", inclusive = true, message = "salaryMax must be >= 0")
  private BigDecimal salaryMax;

  @Column(columnDefinition = "TEXT")
  private String requirements;

  @Column(columnDefinition = "TEXT")
  private String benefits;

  @Column(columnDefinition = "TEXT")
  private String responsibilities;

  private String educationLevel;

  private String experienceLevel;

  private String requiredExperience;

  private String employmentType;

  private String skills;

  private String companyName;

  private Instant reportedAt;
}