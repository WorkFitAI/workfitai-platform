package org.workfitai.jobservice.model.dto.request.JobCategory;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqCreateJobCategoryDTO {

  @NotBlank(message = "Name is required")
  private String name;
}