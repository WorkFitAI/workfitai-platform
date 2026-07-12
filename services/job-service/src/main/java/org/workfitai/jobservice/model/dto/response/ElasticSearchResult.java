package org.workfitai.jobservice.model.dto.response;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ElasticSearchResult {

  private List<UUID> ids;

  private long total;
}