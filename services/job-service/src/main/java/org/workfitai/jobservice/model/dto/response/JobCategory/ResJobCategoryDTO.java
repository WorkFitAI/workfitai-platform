package org.workfitai.jobservice.model.dto.response.JobCategory;

import java.util.UUID;

import org.workfitai.jobservice.model.dto.AuditableResponse;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResJobCategoryDTO implements AuditableResponse {

  private UUID id;

  private String name;

  @JsonIgnore
  @Override
  public String getAuditId() {
    return id.toString();
  }
}