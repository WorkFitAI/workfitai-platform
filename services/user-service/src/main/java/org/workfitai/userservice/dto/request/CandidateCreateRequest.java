package org.workfitai.userservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import org.workfitai.userservice.enums.EUserRole;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateCreateRequest {

  // ===== USER FIELDS =====
  @NotBlank(message = "Full name is required")
  @Size(min = 3, max = 255, message = "Full name must be between 3–255 characters")
  private String fullName;

  @Email(message = "Invalid email format")
  @NotBlank(message = "Email is required")
  private String email;

  @NotBlank(message = "Phone number is required")
  @Pattern(regexp = "^(\\+84)?\\d{10}$", message = "Phone number must be 10 digits or start with +84")
  private String phoneNumber;

  @NotBlank(message = "Password is required")
  private String password;

  @Builder.Default
  private EUserRole userRole = EUserRole.CANDIDATE;

  // ===== CANDIDATE FIELDS =====
  private String careerObjective;
  private String summary;

  @Min(value = 0, message = "Total experience must be >= 0")
  @Max(value = 50, message = "Total experience must be <= 50")
  private int totalExperience;

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
