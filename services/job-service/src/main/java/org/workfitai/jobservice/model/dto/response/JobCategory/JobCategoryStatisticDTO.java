package org.workfitai.jobservice.model.dto.response.JobCategory;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JobCategoryStatisticDTO {
  private UUID jobCategoryId;
  private String name;
  private Long totalJobs;
}