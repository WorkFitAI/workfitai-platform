package org.workfitai.userservice.service.impl;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.userservice.dto.kafka.CompanySyncEvent;
import org.workfitai.userservice.dto.kafka.NotificationEvent;
import org.workfitai.userservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.userservice.dto.request.HRCreateRequest;
import org.workfitai.userservice.dto.request.HRUpdateRequest;
import org.workfitai.userservice.dto.response.HRResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.exception.ApiException;
import org.workfitai.userservice.mapper.HRMapper;
import org.workfitai.userservice.model.HREntity;
import org.workfitai.userservice.repository.HRRepository;
import org.workfitai.userservice.messaging.CompanySyncProducer;
import org.workfitai.userservice.messaging.NotificationProducer;
import org.workfitai.userservice.messaging.UserEventPublisher;
import org.workfitai.userservice.messaging.UserRegistrationProducer;
import org.workfitai.userservice.service.HRService;
import org.workfitai.userservice.specification.HRSpecification;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class HRServiceImpl implements HRService {

  private final HRRepository hrRepository;
  private final HRMapper hrMapper;
  private final HRSpecification hrSpecification;
  private final Validator validator;
  private final CompanySyncProducer companySyncProducer;
  private final NotificationProducer notificationProducer;
  private final UserRegistrationProducer userRegistrationProducer;
  private final UserEventPublisher userEventPublisher;

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

  @Override
  public void createFromKafkaEvent(UserRegistrationEvent.UserData userData) {
    log.info("Creating HR profile from Kafka event for email: {}", userData.getEmail());

    if (userData == null || userData.getHrProfile() == null) {
      throw new ApiException("HR profile details are required", HttpStatus.BAD_REQUEST);
    }

    EUserRole role = EUserRole.fromJson(userData.getRole());
    if (role != EUserRole.HR && role != EUserRole.HR_MANAGER) {
      log.warn("Skipping HR creation for role {}", role);
      return;
    }

    var hrProfile = userData.getHrProfile();
    UUID companyId = null;
    String companyNo = null; // Declare companyNo at the beginning

    // Idempotent check: Try to find existing HR by email or username
    HREntity existingHR = hrRepository.findByEmail(userData.getEmail())
        .or(() -> hrRepository.findByUsername(userData.getUsername()))
        .orElse(null);

    if (existingHR != null) {
      log.info("HR profile already exists with email/username: {}/{}. Updating existing record (idempotent operation)",
          userData.getEmail(), userData.getUsername());

      // Update existing HR with new data from event (idempotent upsert)
      existingHR.setEmail(userData.getEmail());
      existingHR.setUsername(userData.getUsername());
      existingHR.setFullName(userData.getFullName());
      existingHR.setPhoneNumber(userData.getPhoneNumber());
      existingHR.setPasswordHash(userData.getPasswordHash());
      existingHR.setUserStatus(
          userData.getStatus() != null ? EUserStatus.fromDisplayName(userData.getStatus()) : EUserStatus.PENDING);
      existingHR.setDepartment(hrProfile.getDepartment());
      existingHR.setAddress(hrProfile.getAddress());

      HREntity updatedHR = hrRepository.save(existingHR);
      log.info("Successfully updated existing HR profile with ID {} for email: {}",
          updatedHR.getUserId(), userData.getEmail());
      return;
    }

    // Check for phone constraint violations before creating new HR
    if (hrRepository.existsByPhoneNumber(userData.getPhoneNumber())) {
      log.warn(
          "Phone number {} already exists in database but belongs to different user. Skipping creation to prevent constraint violation.",
          userData.getPhoneNumber());
      // Acknowledge message without throwing - this is a business logic issue, not a
      // technical error
      return;
    }

    // For HR role: lookup company from HR Manager's email
    if (role == EUserRole.HR) {
      String hrManagerEmail = hrProfile.getHrManagerEmail();
      if (hrManagerEmail == null || hrManagerEmail.isBlank()) {
        throw new ApiException("HR Manager email is required for HR registration", HttpStatus.BAD_REQUEST);
      }

      HREntity hrManager = hrRepository.findByEmail(hrManagerEmail)
          .orElseThrow(() -> new ApiException(
              "HR Manager with email " + hrManagerEmail + " not found. Please verify the email.",
              HttpStatus.NOT_FOUND));

      if (hrManager.getUserRole() != EUserRole.HR_MANAGER) {
        throw new ApiException(
            "The specified email does not belong to an HR Manager",
            HttpStatus.BAD_REQUEST);
      }

      if (hrManager.getUserStatus() != EUserStatus.ACTIVE) {
        throw new ApiException(
            "HR Manager account is not active. Please contact your HR Manager.",
            HttpStatus.BAD_REQUEST);
      }

      companyId = hrManager.getCompanyId();
      companyNo = hrManager.getCompanyNo(); // Inherit companyNo from HR Manager
      log.info("HR will be assigned to company {} (companyNo: {}) from HR Manager: {}", companyId, companyNo,
          hrManagerEmail);
    }

    // For HR_MANAGER role: use company ID from auth-service
    if (role == EUserRole.HR_MANAGER) {
      if (userData.getCompany() == null) {
        throw new ApiException("Company information is required for HR Manager registration", HttpStatus.BAD_REQUEST);
      }

      // ✅ CRITICAL: Use companyId from auth-service (single source of truth), DON'T
      // generate new UUID
      if (userData.getCompany().getCompanyId() == null || userData.getCompany().getCompanyId().isBlank()) {
        throw new ApiException("Company ID is missing from registration event - auth-service must provide it",
            HttpStatus.INTERNAL_SERVER_ERROR);
      }

      companyId = UUID.fromString(userData.getCompany().getCompanyId());
      companyNo = userData.getCompany().getCompanyNo();
      log.info("Using companyId {} and companyNo {} from auth-service for HR Manager: {}", companyId, companyNo,
          userData.getEmail());
    }

    if (role == EUserRole.HR_MANAGER
        && hrRepository.existsByCompanyIdAndUserRole(companyId, EUserRole.HR_MANAGER)) {
      throw new ApiException("Company already has an HR manager", HttpStatus.CONFLICT);
    }

    HREntity entity = HREntity.builder()
        .fullName(userData.getFullName())
        .email(userData.getEmail())
        .username(userData.getUsername())
        .phoneNumber(userData.getPhoneNumber())
        .passwordHash(userData.getPasswordHash())
        .userRole(role)
        .userStatus(
            userData.getStatus() != null ? EUserStatus.fromDisplayName(userData.getStatus()) : EUserStatus.PENDING)
        .department(hrProfile.getDepartment())
        .companyId(companyId)
        .companyNo(companyNo)
        .address(hrProfile.getAddress())
        .build();

    HREntity savedEntity = hrRepository.save(entity);

    // Publish USER_CREATED event for Elasticsearch sync
    userEventPublisher.publishUserCreated(savedEntity);

    if (role == EUserRole.HR_MANAGER && userData.getCompany() != null) {
      // Set the generated companyId to company data before publishing
      userData.getCompany().setCompanyId(companyId.toString());
      publishCompanySync(userData.getCompany());
    }

    String metadataCompanyId = companyId != null ? companyId.toString() : "";
    notificationProducer.send(NotificationEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType("ACCOUNT_PENDING_APPROVAL")
        .recipientEmail(userData.getEmail())
        .recipientRole(role.name())
        .subject("Your account is pending approval")
        .content("Your " + role.name() + " account is pending approval.")
        .metadata(Map.of("role", role.name(), "companyId", metadataCompanyId))
        .build());
  }

  @Override
  public HRResponse approveHrManager(UUID id, String approver) {
    HREntity entity = hrRepository.findById(id)
        .orElseThrow(() -> new ApiException("HR Manager not found", HttpStatus.NOT_FOUND));
    if (entity.getUserRole() != EUserRole.HR_MANAGER) {
      throw new ApiException("Only HR Manager can be approved via this endpoint", HttpStatus.BAD_REQUEST);
    }
    if (entity.getUserStatus() == EUserStatus.ACTIVE) {
      return hrMapper.toResponse(entity);
    }
    entity.setUserStatus(EUserStatus.ACTIVE);
    entity.setApprovedBy(approver);
    entity.setApprovedAt(Instant.now());
    HREntity saved = hrRepository.save(entity);

    // Publish event to sync status with auth-service
    publishUserStatusUpdate(saved, "HR_MANAGER_APPROVED");

    // Send approval notification email with template
    notificationProducer.send(NotificationEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType("EMAIL_NOTIFICATION")
        .recipientEmail(saved.getEmail())
        .recipientRole(saved.getUserRole().name())
        .templateType("APPROVAL_GRANTED")
        .subject("Your HR Manager account has been approved - WorkFitAI")
        .content("Your HR Manager account is now active.")
        .metadata(Map.of(
            "username", saved.getUsername(),
            "role", "HR Manager",
            "loginUrl", "https://workfitai.com/login",
            "isHRManager", "true"))
        .build());

    // Publish company sync event to job-service
    if (entity.getCompanyId() != null) {
      publishCompanySyncOnApproval(saved);
    }

    return hrMapper.toResponse(saved);
  }

  @Override
  public HRResponse approveHr(UUID id, String approver) {
    HREntity entity = hrRepository.findById(id)
        .orElseThrow(() -> new ApiException("HR not found", HttpStatus.NOT_FOUND));
    if (entity.getUserRole() != EUserRole.HR) {
      throw new ApiException("Only HR can be approved via this endpoint", HttpStatus.BAD_REQUEST);
    }
    if (entity.getUserStatus() == EUserStatus.ACTIVE) {
      return hrMapper.toResponse(entity);
    }

    entity.setUserStatus(EUserStatus.ACTIVE);
    entity.setApprovedBy(approver);
    entity.setApprovedAt(Instant.now());
    HREntity saved = hrRepository.save(entity);

    // Publish event to sync status with auth-service
    publishUserStatusUpdate(saved, "HR_APPROVED");

    // Send approval notification email with template
    notificationProducer.send(NotificationEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType("EMAIL_NOTIFICATION")
        .recipientEmail(saved.getEmail())
        .recipientRole(saved.getUserRole().name())
        .templateType("APPROVAL_GRANTED")
        .subject("Your HR account has been approved - WorkFitAI")
        .content("Your HR account is now active.")
        .metadata(Map.of(
            "username", saved.getUsername(),
            "role", "HR",
            "loginUrl", "https://workfitai.com/login",
            "isHR", "true"))
        .build());

    return hrMapper.toResponse(saved);
  }

  private void publishUserStatusUpdate(HREntity hr, String eventType) {
    UserRegistrationEvent event = UserRegistrationEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType(eventType)
        .timestamp(Instant.now())
        .userData(UserRegistrationEvent.UserData.builder()
            .userId(hr.getUserId().toString())
            .email(hr.getEmail())
            .username(hr.getUsername())
            .role(hr.getUserRole().name())
            .status(hr.getUserStatus().name())
            .build())
        .build();
    userRegistrationProducer.publishUserRegistrationEvent(event);
  }

  private void publishCompanySyncOnApproval(HREntity hrManager) {
    log.info("Publishing company sync event for approved HR Manager: {}", hrManager.getEmail());
    log.info("HR Manager company details - companyId: {}, companyNo: {}",
        hrManager.getCompanyId(), hrManager.getCompanyNo());

    CompanySyncEvent event = CompanySyncEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType("COMPANY_UPSERT")
        .company(CompanySyncEvent.CompanyData.builder()
            .companyId(hrManager.getCompanyId().toString())
            .companyNo(hrManager.getCompanyNo()) // Add companyNo (primary key for job-service)
            .name(hrManager.getFullName() + "'s Company") // Will be overwritten if company data exists
            .address(hrManager.getAddress())
            .build())
        .build();

    log.info("Publishing event with companyId: {}, companyNo: {}",
        event.getCompany().getCompanyId(), event.getCompany().getCompanyNo());
    companySyncProducer.publish(event);
  }

  private void publishCompanySync(UserRegistrationEvent.CompanyData companyData) {
    CompanySyncEvent event = CompanySyncEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType("COMPANY_UPSERT")
        .company(CompanySyncEvent.CompanyData.builder()
            .companyId(companyData.getCompanyId())
            .companyNo(companyData.getCompanyNo()) // Add companyNo field
            .name(companyData.getName())
            .logoUrl(companyData.getLogoUrl())
            .websiteUrl(companyData.getWebsiteUrl())
            .description(companyData.getDescription())
            .address(companyData.getAddress())
            .size(companyData.getSize())
            .build())
        .build();
    companySyncProducer.publish(event);
  }

  @Override
  @Transactional
  public void updateStatus(String email, EUserStatus status) {
    log.info("Updating HR status for email: {} to status: {}", email, status);

    HREntity hr = hrRepository.findByEmail(email)
        .orElseThrow(() -> new ApiException("HR not found with email: " + email, HttpStatus.NOT_FOUND));

    hr.setUserStatus(status);
    hrRepository.save(hr);

    log.info("Successfully updated HR status for email: {} to status: {}", email, status);
  }

  @Override
  public HRResponse approveHrManagerByUsername(String username, String approver) {
    HREntity hr = hrRepository.findByUsername(username)
        .orElseThrow(() -> new ApiException("HR Manager not found with username: " + username, HttpStatus.NOT_FOUND));
    return approveHrManager(hr.getId(), approver);
  }

  @Override
  public HRResponse approveHrByUsername(String username, String approver) {
    HREntity hr = hrRepository.findByUsername(username)
        .orElseThrow(() -> new ApiException("HR not found with username: " + username, HttpStatus.NOT_FOUND));
    return approveHr(hr.getId(), approver);
  }

  @Override
  public HRResponse getByUsername(String username) {
    return hrRepository.findByUsername(username)
        .map(hrMapper::toResponse)
        .orElseThrow(() -> new ApiException("HR not found with username: " + username, HttpStatus.NOT_FOUND));
  }
}
