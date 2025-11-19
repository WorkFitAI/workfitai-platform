package org.workfitai.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.workfitai.userservice.enums.ESkillLevel;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CandidateProfileSkillResponse {
  private UUID id;
  private UUID skillId;
  private ESkillLevel level;
  private int yearsOfExperience;
  private Instant addedAt;
}
