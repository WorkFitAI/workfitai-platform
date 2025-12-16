package org.workfitai.userservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.workfitai.userservice.dto.request.CandidateCreateRequest;
import org.workfitai.userservice.dto.request.CandidateUpdateRequest;
import org.workfitai.userservice.dto.response.CandidateResponse;
import org.workfitai.userservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.userservice.enums.EUserStatus;

import java.util.Map;
import java.util.UUID;

public interface CandidateService {
  CandidateResponse create(CandidateCreateRequest dto);

  CandidateResponse update(UUID id, CandidateUpdateRequest dto);

  void delete(UUID id);

  CandidateResponse getById(UUID id);

  /**
   * Search candidates by keyword
   */
  Page<CandidateResponse> search(String keyword, Pageable pageable);

  /**
   * Filter candidates by education and experience
   */
  Page<CandidateResponse> filter(String education, Integer minExp, Integer maxExp, Pageable pageable);

  Map<String, Long> getExperienceStats();

  /**
   * Create candidate from Kafka user registration event
   */
  void createFromKafkaEvent(UserRegistrationEvent.UserData userData);

  /**
   * Update candidate status by email
   */
  void updateStatus(String email, EUserStatus status);
}
