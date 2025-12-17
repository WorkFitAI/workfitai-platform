package org.workfitai.userservice.service.impl;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.userservice.dto.request.AdminCreateRequest;
import org.workfitai.userservice.dto.request.AdminUpdateRequest;
import org.workfitai.userservice.dto.response.AdminResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.exception.ApiException;
import org.workfitai.userservice.mapper.AdminMapper;
import org.workfitai.userservice.model.AdminEntity;
import org.workfitai.userservice.repository.AdminRepository;
import org.workfitai.userservice.service.AdminService;
import org.workfitai.userservice.specification.AdminSpecification;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminServiceImpl implements AdminService {

  private final AdminRepository adminRepository;
  private final AdminMapper adminMapper;
  private final AdminSpecification adminSpecification;
  private final Validator validator;

  private <T> void validate(T dto) {
    Set<ConstraintViolation<T>> violations = validator.validate(dto);
    if (!violations.isEmpty()) {
      String msg = violations.stream()
          .map(v -> v.getPropertyPath() + ": " + v.getMessage())
          .collect(Collectors.joining("; "));
      throw new ApiException("Validation failed: " + msg, HttpStatus.BAD_REQUEST);
    }
  }

  @Override
  public AdminResponse create(AdminCreateRequest dto) {
    validate(dto);

    if (adminRepository.existsByEmail(dto.getEmail())) {
      throw new ApiException("Email already exists", HttpStatus.CONFLICT);
    }

    AdminEntity entity = adminMapper.toEntity(dto);
    entity.setUserRole(EUserRole.ADMIN);

    return adminMapper.toResponse(adminRepository.save(entity));
  }

  @Override
  public AdminResponse update(UUID id, AdminUpdateRequest dto) {
    validate(dto);

    AdminEntity existing = adminRepository.findById(id)
        .orElseThrow(() -> new ApiException("Admin not found", HttpStatus.NOT_FOUND));

    adminMapper.updateEntityFromUpdateRequest(dto, existing);

    return adminMapper.toResponse(adminRepository.save(existing));
  }

  @Override
  public void delete(UUID id) {
    if (!adminRepository.existsById(id)) {
      throw new ApiException("Admin not found", HttpStatus.NOT_FOUND);
    }
    adminRepository.deleteById(id);
  }

  @Override
  public AdminResponse getById(UUID id) {
    return adminRepository.findById(id)
        .map(adminMapper::toResponse)
        .orElseThrow(() -> new ApiException("Admin not found", HttpStatus.NOT_FOUND));
  }

  @Override
  public Page<AdminResponse> search(String keyword, Pageable pageable) {
    Specification<AdminEntity> spec = adminSpecification.filter(keyword);
    return adminRepository.findAll(spec, pageable).map(adminMapper::toResponse);
  }
}
