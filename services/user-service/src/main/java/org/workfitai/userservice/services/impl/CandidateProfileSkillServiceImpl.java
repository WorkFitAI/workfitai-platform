package org.workfitai.userservice.services.impl;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.userservice.dto.request.CandidateProfileSkillRequest;
import org.workfitai.userservice.dto.request.CandidateProfileSkillUpdateRequest;
import org.workfitai.userservice.dto.response.CandidateProfileSkillResponse;
import org.workfitai.userservice.exceptions.ApiException;
import org.workfitai.userservice.mapper.CandidateProfileSkillMapper;
import org.workfitai.userservice.model.CandidateEntity;
import org.workfitai.userservice.model.CandidateProfileSkillEntity;
import org.workfitai.userservice.repository.CandidateProfileSkillRepository;
import org.workfitai.userservice.repository.CandidateRepository;
import org.workfitai.userservice.services.CandidateProfileSkillService;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CandidateProfileSkillServiceImpl implements CandidateProfileSkillService {

  private final CandidateProfileSkillRepository skillRepository;
  private final CandidateRepository candidateRepository;
  private final CandidateProfileSkillMapper mapper;
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

  @Override
  public CandidateProfileSkillResponse create(UUID candidateId, CandidateProfileSkillRequest dto) {
    validateDto(dto);
    CandidateEntity candidate = candidateRepository.findById(candidateId)
        .orElseThrow(() -> new ApiException("Candidate not found with id: " + candidateId, HttpStatus.NOT_FOUND));

    // Kiểm tra trùng kỹ năng
    boolean exists = skillRepository.existsByCandidate_UserIdAndSkillId(candidateId, dto.getSkillId());
    if (exists) {
      throw new ApiException("This candidate already has the selected skill.", HttpStatus.CONFLICT);
    }

    CandidateProfileSkillEntity entity = mapper.toEntity(dto);
    entity.setCandidate(candidate);

    return mapper.toResponse(skillRepository.save(entity));
  }

  @Override
  public CandidateProfileSkillResponse update(UUID id, CandidateProfileSkillUpdateRequest dto) {
    validateDto(dto);

    CandidateProfileSkillEntity existing = skillRepository.findById(id)
        .orElseThrow(() -> new ApiException("Candidate skill not found with id: " + id, HttpStatus.NOT_FOUND));

    // Nếu có thay đổi skillId thì check trùng
    if (dto.getSkillId() != null && !dto.getSkillId().equals(existing.getSkillId())) {
      boolean duplicate = skillRepository.existsByCandidate_UserIdAndSkillId(existing.getCandidate().getUserId(), dto.getSkillId());
      if (duplicate) {
        throw new ApiException("Candidate already has a skill with this ID.", HttpStatus.CONFLICT);
      }
      existing.setSkillId(dto.getSkillId());
    }

    if (dto.getLevel() != null) existing.setLevel(dto.getLevel());
    if (dto.getYearsOfExperience() != null) {
      if (dto.getYearsOfExperience() < 0 || dto.getYearsOfExperience() > 50) {
        throw new ApiException("Years of experience must be between 0 and 50.", HttpStatus.BAD_REQUEST);
      }
      existing.setYearsOfExperience(dto.getYearsOfExperience());
    }

    return mapper.toResponse(skillRepository.save(existing));
  }

  @Override
  public void delete(UUID id) {
    CandidateProfileSkillEntity entity = skillRepository.findById(id)
        .orElseThrow(() -> new ApiException("Candidate skill not found with id: " + id, HttpStatus.NOT_FOUND));
    skillRepository.delete(entity);
  }

  @Override
  public CandidateProfileSkillResponse getById(UUID id) {
    return skillRepository.findById(id)
        .map(mapper::toResponse)
        .orElseThrow(() -> new ApiException("Candidate skill not found with id: " + id, HttpStatus.NOT_FOUND));
  }

  @Override
  @Transactional(readOnly = true)
  public List<CandidateProfileSkillResponse> getAllByCandidate(UUID candidateId) {
    CandidateEntity candidate = candidateRepository.findById(candidateId)
        .orElseThrow(() -> new ApiException("Candidate not found with id: " + candidateId, HttpStatus.NOT_FOUND));

    return skillRepository.findAllByCandidate_UserId(candidate.getUserId())
        .stream().map(mapper::toResponse)
        .toList();
  }
}
