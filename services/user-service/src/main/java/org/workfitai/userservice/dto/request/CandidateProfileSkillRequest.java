package org.workfitai.userservice.dto.request;


import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.workfitai.userservice.enums.ESkillLevel;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateProfileSkillRequest {

  @NotNull(message = "Skill ID must not be null")
  private UUID skillId;

  @NotNull(message = "Skill level is required")
  private ESkillLevel level;

  @Min(value = 0, message = "Years of experience must be >= 0")
  @Max(value = 50, message = "Years of experience must be <= 50")
  private int yearsOfExperience;
}
