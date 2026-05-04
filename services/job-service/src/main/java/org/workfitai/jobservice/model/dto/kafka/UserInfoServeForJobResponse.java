package org.workfitai.jobservice.model.dto.kafka;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfoServeForJobResponse {
  private UUID userId;
  private String username;
  private String email;
}
