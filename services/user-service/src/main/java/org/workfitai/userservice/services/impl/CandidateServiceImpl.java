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
import org.springframework.util.StringUtils;
import org.workfitai.userservice.dto.request.CandidateCreateRequest;
import org.workfitai.userservice.dto.request.CandidateUpdateRequest;
import org.workfitai.userservice.dto.response.CandidateResponse;
import org.workfitai.userservice.exceptions.ApiException;
import org.workfitai.userservice.mapper.CandidateMapper;
import org.workfitai.userservice.model.CandidateEntity;
import org.workfitai.userservice.repository.CandidateRepository;
import org.workfitai.userservice.services.CandidateService;
import org.workfitai.userservice.specification.CandidateSpecification;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CandidateServiceImpl implements CandidateService {

  private final CandidateRepository candidateRepository;
  private final CandidateMapper candidateMapper;
  private final Validator validator;

  private <T> void validateDto(T dto) {
    Set<ConstraintViolation<T>> violations = validator.validate(dto);
    if (!violations.isEmpty()) {
      String msg = violations.stream()
          .map(v -> v.getPropertyPath() + ": " + v.getMessage())
          .collect(Collectors.joining("; "));
      throw new ApiException("Validation error: " + msg, HttpStatus.BAD_REQUEST);
    }
  }

  private void validateBusinessRules(CandidateCreateRequest dto) {
    if (dto.getTotalExperience() < 0 || dto.getTotalExperience() > 50) {
      throw new ApiException("Total experience must be between 0 and 50 years.", HttpStatus.BAD_REQUEST);
    }
    if (candidateRepository.existsByEmail(dto.getEmail())) {
      throw new ApiException("Email already exists: " + dto.getEmail(), HttpStatus.CONFLICT);
    }
    if (candidateRepository.existsByPhoneNumber(dto.getPhoneNumber())) {
      throw new ApiException("Phone number already exists: " + dto.getPhoneNumber(), HttpStatus.CONFLICT);
    }
  }

  private void validateBusinessRulesForUpdate(CandidateEntity current, CandidateUpdateRequest dto) {
    if (dto.getTotalExperience() != null) {
      if (dto.getTotalExperience() < 0 || dto.getTotalExperience() > 50) {
        throw new ApiException("Total experience must be between 0 and 50 years.", HttpStatus.BAD_REQUEST);
      }
    }

    if (StringUtils.hasText(dto.getEmail()) && !dto.getEmail().equalsIgnoreCase(current.getEmail())) {
      if (candidateRepository.existsByEmail(dto.getEmail())) {
        throw new ApiException("Email already exists: " + dto.getEmail(), HttpStatus.CONFLICT);
      }
    }

    if (StringUtils.hasText(dto.getPhoneNumber()) && !dto.getPhoneNumber().equalsIgnoreCase(current.getPhoneNumber())) {
      if (candidateRepository.existsByPhoneNumber(dto.getPhoneNumber())) {
        throw new ApiException("Phone number already exists: " + dto.getPhoneNumber(), HttpStatus.CONFLICT);
      }
    }
  }

  @Override
  public CandidateResponse create(CandidateCreateRequest dto) {
    validateDto(dto);
    validateBusinessRules(dto);
    CandidateEntity entity = candidateMapper.toEntity(dto);
    return candidateMapper.toResponse(candidateRepository.save(entity));
  }

  @Override
  public CandidateResponse update(UUID id, CandidateUpdateRequest dto) {
    validateDto(dto);

    CandidateEntity current = candidateRepository.findById(id)
        .orElseThrow(() -> new ApiException("Candidate not found with id: " + id, HttpStatus.NOT_FOUND));

    validateBusinessRulesForUpdate(current, dto);

    candidateMapper.updateEntityFromUpdateRequest(dto, current);
    return candidateMapper.toResponse(candidateRepository.save(current));
  }

  @Override
  public void delete(UUID id) {
    CandidateEntity entity = candidateRepository.findById(id)
        .orElseThrow(() -> new ApiException("Candidate not found with id: " + id, HttpStatus.NOT_FOUND));
    candidateRepository.delete(entity);
  }

  @Override
  public CandidateResponse getById(UUID id) {
    CandidateEntity entity = candidateRepository.findById(id)
        .orElseThrow(() -> new ApiException("Candidate not found with id: " + id, HttpStatus.NOT_FOUND));
    return candidateMapper.toResponse(entity);
  }

  @Override
  public Page<CandidateResponse> search(String keyword, Pageable pageable) {
    Specification<CandidateEntity> spec = CandidateSpecification.search(keyword);
    return candidateRepository.findAll(spec, pageable)
        .map(candidateMapper::toResponse);
  }

  @Override
  public Page<CandidateResponse> filter(String education, Integer minExp, Integer maxExp, Pageable pageable) {
    if (minExp != null && maxExp != null && minExp > maxExp) {
      throw new ApiException("Minimum experience cannot be greater than maximum experience.", HttpStatus.BAD_REQUEST);
    }
    Specification<CandidateEntity> spec = CandidateSpecification.filter(education, minExp, maxExp);
    return candidateRepository.findAll(spec, pageable)
        .map(candidateMapper::toResponse);
  }

  @Override
  public Map<String, Long> getExperienceStats() {
    List<Object[]> results = candidateRepository.countByExperienceRange();

    // Convert raw query result -> Map<String, Long>
    return results.stream()
        .collect(Collectors.toMap(
            row -> (String) row[0],
            row -> (Long) row[1]
        ));
  }

}
