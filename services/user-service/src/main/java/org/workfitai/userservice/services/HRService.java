package org.workfitai.userservice.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.workfitai.userservice.dto.request.HRCreateRequest;
import org.workfitai.userservice.dto.request.HRUpdateRequest;
import org.workfitai.userservice.dto.response.HRResponse;

import java.util.Map;
import java.util.UUID;

public interface HRService {
  HRResponse create(HRCreateRequest dto);

  HRResponse update(UUID id, HRUpdateRequest dto);

  void delete(UUID id);

  HRResponse getById(UUID id);

  Page<HRResponse> search(String keyword, Pageable pageable);

  Map<String, Long> countByDepartment();
}
