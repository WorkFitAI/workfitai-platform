package org.workfitai.jobservice.model.dto.request.JobCategory;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqUpdateJobCategoryDTO {

  @NotNull
  private UUID id;

  @NotBlank(message = "Name is required")
  private String name;
}