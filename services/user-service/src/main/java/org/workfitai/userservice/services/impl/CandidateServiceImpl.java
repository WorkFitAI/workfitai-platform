package org.workfitai.userservice.services.impl;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.workfitai.userservice.constants.ValidationMessages;
import org.workfitai.userservice.dto.request.CandidateCreateRequest;
import org.workfitai.userservice.dto.request.CandidateUpdateRequest;
import org.workfitai.userservice.dto.response.CandidateResponse;
import org.workfitai.userservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
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
@Slf4j
public class CandidateServiceImpl implements CandidateService {

  private final CandidateRepository candidateRepository;
  private final CandidateMapper candidateMapper;
  private final Validator validator;
  private final PasswordEncoder passwordEncoder;

  private <T> void validateDto(T dto) {
    Set<ConstraintViolation<T>> violations = validator.validate(dto);
    if (!violations.isEmpty()) {
      List<String> errorMessages = violations.stream()
          .map(v -> v.getPropertyPath() + ": " + v.getMessage())
          .collect(Collectors.toList());
      throw ApiException.validationError(errorMessages);
    }
  }

  private void validateBusinessRules(CandidateCreateRequest dto) {
    if (dto.getTotalExperience() < 0 || dto.getTotalExperience() > 50) {
      throw new ApiException(ValidationMessages.Experience.INVALID_RANGE, HttpStatus.BAD_REQUEST);
    }
    if (candidateRepository.existsByEmail(dto.getEmail())) {
      throw ApiException.conflict(ValidationMessages.CANDIDATE_EMAIL_DUPLICATE);
    }
    if (candidateRepository.existsByPhoneNumber(dto.getPhoneNumber())) {
      throw ApiException.conflict(ValidationMessages.CANDIDATE_PHONENUMBER_DUPLICATE);
    }
  }

  private void validateBusinessRulesForUpdate(CandidateEntity current, CandidateUpdateRequest dto) {
    if (dto.getTotalExperience() != null) {
      if (dto.getTotalExperience() < 0 || dto.getTotalExperience() > 50) {
        throw new ApiException(ValidationMessages.Experience.INVALID_RANGE, HttpStatus.BAD_REQUEST);
      }
    }

    if (StringUtils.hasText(dto.getEmail()) && !dto.getEmail().equalsIgnoreCase(current.getEmail())) {
      if (candidateRepository.existsByEmail(dto.getEmail())) {
        throw ApiException.conflict(ValidationMessages.CANDIDATE_EMAIL_DUPLICATE);
      }
    }

    if (StringUtils.hasText(dto.getPhoneNumber()) && !dto.getPhoneNumber().equalsIgnoreCase(current.getPhoneNumber())) {
      if (candidateRepository.existsByPhoneNumber(dto.getPhoneNumber())) {
        throw ApiException.conflict(ValidationMessages.CANDIDATE_PHONENUMBER_DUPLICATE);
      }
    }
  }

  @Override
  public CandidateResponse create(CandidateCreateRequest dto) {
    log.info("Creating candidate with email: {}", dto.getEmail());

    try {
      validateDto(dto);
      validateBusinessRules(dto);

      log.debug("Validation passed, mapping to entity...");
      CandidateEntity entity = candidateMapper.toEntity(dto);

      // Hash password manually
      entity.setPasswordHash(passwordEncoder.encode(dto.getPassword()));

      log.debug("Saving entity to database...");
      CandidateEntity savedEntity = candidateRepository.save(entity);

      log.info("Successfully created candidate with ID: {}", savedEntity.getId());
      return candidateMapper.toResponse(savedEntity);

    } catch (Exception ex) {
      log.error("Error creating candidate with email: {}. Error: {}", dto.getEmail(), ex.getMessage(), ex);
      throw ex; // Re-throw để RestExceptionHandler xử lý
    }
  }

  @Override
  public CandidateResponse update(UUID id, CandidateUpdateRequest dto) {
    validateDto(dto);

    CandidateEntity current = candidateRepository.findById(id)
        .orElseThrow(() -> ApiException.notFound("Candidate", id.toString()));

    validateBusinessRulesForUpdate(current, dto);

    candidateMapper.updateEntityFromUpdateRequest(dto, current);
    return candidateMapper.toResponse(candidateRepository.save(current));
  }

  @Override
  public void delete(UUID id) {
    CandidateEntity entity = candidateRepository.findById(id)
        .orElseThrow(() -> ApiException.notFound("Candidate", id.toString()));
    candidateRepository.delete(entity);
  }

  @Override
  public CandidateResponse getById(UUID id) {
    CandidateEntity entity = candidateRepository.findById(id)
        .orElseThrow(() -> ApiException.notFound("Candidate", id.toString()));
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
            row -> (Long) row[1]));
  }

  @Override
  @Transactional
  public void createFromKafkaEvent(UserRegistrationEvent.UserData userData) {
    log.info("Creating candidate from Kafka event for user: {}", userData.getEmail());

    try {
      if (userData.getRole() != null
          && !EUserRole.CANDIDATE.name().equalsIgnoreCase(userData.getRole().replace(" ", "_"))) {
        log.warn("Skipping candidate creation because role {} is not CANDIDATE", userData.getRole());
        throw new ApiException("Invalid role for candidate creation", HttpStatus.BAD_REQUEST);
      }

      // Check if candidate already exists by email
      if (candidateRepository.existsByEmail(userData.getEmail())) {
        log.warn("Candidate with email {} already exists, skipping", userData.getEmail());
        return;
      }

      // Map status from string to enum
      EUserStatus status = userData.getStatus() != null ? EUserStatus.fromJson(userData.getStatus())
          : EUserStatus.ACTIVE;

      // Create candidate entity from user data - let JPA handle ID generation
      CandidateEntity candidate = CandidateEntity.builder()
          .email(userData.getEmail())
          .username(userData.getUsername())
          .fullName(userData.getFullName())
          .phoneNumber(userData.getPhoneNumber())
          .passwordHash(userData.getPasswordHash())
          .userRole(EUserRole.CANDIDATE) // Set required user role
          .userStatus(status) // Use status from event
          .isDeleted(false) // Set deleted flag to false
          .build();

      CandidateEntity savedCandidate = candidateRepository.save(candidate);

      log.info("Successfully created candidate with ID {} for email: {}",
          savedCandidate.getUserId(), userData.getEmail());

    } catch (Exception ex) {
      log.error("Error creating candidate from Kafka event for email: {}", userData.getEmail(), ex);
      throw new ApiException("Failed to create candidate from user registration event: " + ex.getMessage(),
          HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  @Transactional
  public void updateStatus(String email, EUserStatus status) {
    log.info("Updating candidate status for email: {} to status: {}", email, status);

    CandidateEntity candidate = candidateRepository.findByEmail(email)
        .orElseThrow(() -> new ApiException("Candidate not found with email: " + email, HttpStatus.NOT_FOUND));

    candidate.setUserStatus(status);
    candidateRepository.save(candidate);

    log.info("Successfully updated candidate status for email: {} to status: {}", email, status);
  }

}
