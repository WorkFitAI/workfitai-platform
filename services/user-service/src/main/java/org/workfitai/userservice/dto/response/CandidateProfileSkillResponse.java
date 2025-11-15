package org.workfitai.userservice.dto.response;

import lombok.*;
import org.workfitai.userservice.enums.ESkillLevel;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateProfileSkillResponse {
  private UUID id;
  private UUID skillId;
  private ESkillLevel level;
  private int yearsOfExperience;
  private Instant addedAt;
}
