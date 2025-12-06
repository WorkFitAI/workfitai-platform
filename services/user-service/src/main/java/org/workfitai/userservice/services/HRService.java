package org.workfitai.userservice.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.workfitai.userservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.userservice.dto.request.HRCreateRequest;
import org.workfitai.userservice.dto.request.HRUpdateRequest;
import org.workfitai.userservice.dto.response.HRResponse;
import org.workfitai.userservice.enums.EUserStatus;

import java.util.Map;
import java.util.UUID;

public interface HRService {
  HRResponse create(HRCreateRequest dto);

  HRResponse update(UUID id, HRUpdateRequest dto);

  void delete(UUID id);

  HRResponse getById(UUID id);

  Page<HRResponse> search(String keyword, Pageable pageable);

  // Method to create HR from Kafka event
  void createFromKafkaEvent(UserRegistrationEvent.UserData userData);

  Map<String, Long> countByDepartment();

  /**
   * Admin approves HR manager registration.
   */
  HRResponse approveHrManager(UUID id, String approver);

  /**
   * HR Manager approves HR registration.
   */
  HRResponse approveHr(UUID id, String approver);

  /**
   * Admin approves HR manager by username.
   */
  HRResponse approveHrManagerByUsername(String username, String approver);

  /**
   * HR Manager approves HR by username.
   */
  HRResponse approveHrByUsername(String username, String approver);

  /**
   * Get HR by username.
   */
  HRResponse getByUsername(String username);

  /**
   * Update HR status by email
   */
  void updateStatus(String email, EUserStatus status);
}
