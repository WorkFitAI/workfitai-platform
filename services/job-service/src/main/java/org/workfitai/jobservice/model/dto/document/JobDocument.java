package org.workfitai.jobservice.model.dto.document;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDocument {

  private UUID jobId;
  private String title;
  private String description;
  private String shortDescription;
  private String employmentType;
  private String experienceLevel;
  private String requiredExperience;
  private BigDecimal salaryMin;
  private BigDecimal salaryMax;
  private String currency;
  private String location;
  private String status;
  private String educationLevel;
  private String benefits;
  private String requirements;
  private String responsibilities;
  private Long views;
  private String companyNo;
  private String companyName;
  private UUID categoryId;
  private String categoryName;
  private List<String> skills;
  private Instant createdDate;
  private Instant expiresAt;
  private Boolean deleted;
}