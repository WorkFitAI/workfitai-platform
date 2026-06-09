package org.workfitai.jobservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_report_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobReportSnapshot {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID snapshotId;

  private UUID jobId;

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

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "report_id", nullable = false, unique = true)
  private Report report;
}