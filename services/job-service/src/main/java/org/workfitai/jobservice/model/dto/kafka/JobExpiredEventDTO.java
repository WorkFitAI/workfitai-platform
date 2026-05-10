package org.workfitai.jobservice.model.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobExpiredEventDTO {
  private String jobId;
  private String createdBy;
  private String email;
  private String jobTitle;
  private String companyName;
}
