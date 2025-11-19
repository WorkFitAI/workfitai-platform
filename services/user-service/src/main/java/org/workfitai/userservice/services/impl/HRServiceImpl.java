package org.workfitai.userservice.services.impl;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.userservice.dto.request.HRCreateRequest;
import org.workfitai.userservice.dto.request.HRUpdateRequest;
import org.workfitai.userservice.dto.response.HRResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.exceptions.ApiException;
import org.workfitai.userservice.mapper.HRMapper;
import org.workfitai.userservice.model.HREntity;
import org.workfitai.userservice.repository.HRRepository;
import org.workfitai.userservice.services.HRService;
import org.workfitai.userservice.specification.HRSpecification;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class HRServiceImpl implements HRService {

  private final HRRepository hrRepository;
  private final HRMapper hrMapper;
  private final HRSpecification hrSpecification;
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
  public HRResponse create(HRCreateRequest dto) {
    validate(dto);
    if (hrRepository.existsByEmail(dto.getEmail())) {
      throw new ApiException("Email already exists", HttpStatus.CONFLICT);
    }

    HREntity entity = hrMapper.toEntity(dto);
    entity.setUserRole(EUserRole.HR);

    return hrMapper.toResponse(hrRepository.save(entity));
  }

  @Override
  public HRResponse update(UUID id, HRUpdateRequest dto) {
    validate(dto);

    HREntity existing = hrRepository.findById(id)
        .orElseThrow(() -> new ApiException("HR not found", HttpStatus.NOT_FOUND));

    hrMapper.updateEntityFromUpdateRequest(dto, existing);

    return hrMapper.toResponse(hrRepository.save(existing));
  }

  @Override
  public void delete(UUID id) {
    if (!hrRepository.existsById(id)) {
      throw new ApiException("HR not found", HttpStatus.NOT_FOUND);
    }
    hrRepository.deleteById(id);
  }

  @Override
  public HRResponse getById(UUID id) {
    return hrRepository.findById(id)
        .map(hrMapper::toResponse)
        .orElseThrow(() -> new ApiException("HR not found", HttpStatus.NOT_FOUND));
  }

  @Override
  public Page<HRResponse> search(String keyword, Pageable pageable) {
    Specification<HREntity> spec = hrSpecification.filter(keyword);
    return hrRepository.findAll(spec, pageable).map(hrMapper::toResponse);
  }

  @Override
  public Map<String, Long> countByDepartment() {
    return hrRepository.countByDepartment();
  }
}
