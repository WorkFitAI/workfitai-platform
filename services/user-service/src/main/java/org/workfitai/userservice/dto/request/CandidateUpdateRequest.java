package org.workfitai.userservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateUpdateRequest {

  // ===== USER FIELDS =====
  @Size(min = 3, max = 255, message = "Full name must be between 3–255 characters")
  private String fullName;

  @Email(message = "Invalid email format")
  private String email;

  @Pattern(regexp = "^(\\+84)?\\d{10}$", message = "Invalid phone number format")
  private String phoneNumber;

  // ===== CANDIDATE FIELDS =====
  private String careerObjective;
  private String summary;

  @Min(value = 0, message = "Total experience must be >= 0")
  @Max(value = 50, message = "Total experience must be <= 50")
  private Integer totalExperience;

  private String education;
  private String certifications;
  private String portfolioLink;
  private String linkedinUrl;
  private String githubUrl;

  @Size(max = 255, message = "Expected position must not exceed 255 characters")
  private String expectedPosition;

  private List<String> cvIds;
  private List<CandidateProfileSkillRequest> skills;
}
