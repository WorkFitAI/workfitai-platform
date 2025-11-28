package org.workfitai.userservice.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.workfitai.userservice.dto.request.AdminCreateRequest;
import org.workfitai.userservice.dto.request.AdminUpdateRequest;
import org.workfitai.userservice.dto.response.AdminResponse;

import java.util.UUID;

public interface AdminService {
  AdminResponse create(AdminCreateRequest dto);

  AdminResponse update(UUID id, AdminUpdateRequest dto);

  void delete(UUID id);

  AdminResponse getById(UUID id);

  Page<AdminResponse> search(String keyword, Pageable pageable);
}
