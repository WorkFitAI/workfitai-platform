package org.workfitai.userservice.dto.response;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CandidateResponse extends UserBaseResponse {

  private String careerObjective;
  private String summary;
  private int totalExperience;
  private String education;
  private String certifications;
  private String portfolioLink;
  private String linkedinUrl;
  private String githubUrl;
  private String expectedPosition;

  private List<String> cvIds;
  private List<CandidateProfileSkillResponse> skills;
}
