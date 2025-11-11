package org.workfitai.userservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Entity
@Table(name = "candidates")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@PrimaryKeyJoinColumn(name = "user_id")
public class CandidateEntity extends UserEntity {

  @Size(max = 2000, message = "Career objective must not exceed 2000 characters")
  @Column(name = "career_objective", columnDefinition = "TEXT")
  private String careerObjective;

  @Size(max = 3000, message = "Summary must not exceed 3000 characters")
  @Column(name = "summary", columnDefinition = "TEXT")
  private String summary;

  @Min(value = 0, message = "Total experience must be >= 0")
  @Max(value = 50, message = "Total experience must be <= 50")
  @Column(name = "total_experience")
  private int totalExperience;

  @Size(max = 2000, message = "Education field must not exceed 2000 characters")
  @Column(name = "education", columnDefinition = "TEXT")
  private String education;

  @Size(max = 2000, message = "Certifications field must not exceed 2000 characters")
  @Column(name = "certifications", columnDefinition = "TEXT")
  private String certifications;

  @Pattern(
      regexp = "^(https?://)?(www\\.)?[a-zA-Z0-9\\-]+\\.[a-zA-Z]{2,}(/.*)?$",
      message = "Portfolio link must be a valid URL"
  )
  @Column(name = "portfolio_link")
  private String portfolioLink;

  @Pattern(
      regexp = "^(https?://)?(www\\.)?linkedin\\.com/.*$",
      message = "LinkedIn URL must be a valid LinkedIn profile link"
  )
  @Column(name = "linkedin_url")
  private String linkedinUrl;

  @Pattern(
      regexp = "^(https?://)?(www\\.)?github\\.com/.*$",
      message = "GitHub URL must be a valid GitHub profile link"
  )
  @Column(name = "github_url")
  private String githubUrl;

  @Size(max = 255, message = "Expected position must not exceed 255 characters")
  @Column(name = "expected_position")
  private String expectedPosition;

  /**
   * Chỉ lưu danh sách ID của CV từ cv-service.
   * Dữ liệu chi tiết sẽ được lấy qua API call hoặc message broker.
   */
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "candidate_cv_refs",
      joinColumns = @JoinColumn(name = "candidate_id")
  )
  @Column(name = "cv_id")
  private List<String> cvIds;

  /**
   * Quan hệ nội bộ giữa Candidate và CandidateProfileSkill
   * (có metadata: level, năm kinh nghiệm, thời điểm thêm)
   */
  @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL,
      orphanRemoval = true, fetch = FetchType.LAZY)
  private List<CandidateProfileSkillEntity> skills;
}
