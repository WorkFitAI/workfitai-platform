package org.workfitai.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.workfitai.authservice.enums.UserRole;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.messaging.CompanyProducer;
import org.workfitai.authservice.messaging.NotificationProducer;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.service.ApprovalService;
import org.workfitai.authservice.messaging.UserRegistrationProducer;
import org.workfitai.authservice.dto.kafka.NotificationEvent;
import org.workfitai.authservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.authservice.dto.kafka.CompanyCreationEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalServiceImpl implements ApprovalService {

    private final UserRepository userRepository;
    private final UserRegistrationProducer userRegistrationProducer;
    private final NotificationProducer notificationProducer;
    private final CompanyProducer companyProducer;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Override
    public List<Object> getPendingApprovals() {
        List<User> pendingUsers = userRepository.findByStatus(UserStatus.WAIT_APPROVED);

        return pendingUsers.stream()
                .map(user -> Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "email", user.getEmail(),
                        "role", user.getRoles().iterator().next(),
                        "createdAt", user.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Override
    public void approveHRManager(String userId, String approvedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getStatus() != UserStatus.WAIT_APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not waiting for approval");
        }

        UserRole role = UserRole.fromString(user.getRoles().iterator().next());
        if (role != UserRole.HR_MANAGER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not an HR Manager");
        }

        // Update user status to active
        user.setStatus(UserStatus.ACTIVE);
        user.setUpdatedAt(Instant.now());
        User savedUser = userRepository.save(user);

        // Send approval event to user-service to update status there
        publishUserApprovalEvent(savedUser, "HR_MANAGER_APPROVED");

        // If HR_MANAGER, create company in job-service
        if (savedUser.getCompanyNo() != null) {
            createCompany(savedUser);
        }

        // Send email notification
        sendApprovalNotification(savedUser.getEmail(), savedUser.getUsername(), "HR Manager");

        log.info("HR Manager {} approved by {}", userId, approvedBy);
    }

    private void createCompany(User user) {
        try {
            // ✅ FIX: Use companyId from User entity (already saved in MongoDB during
            // registration)
            // DO NOT generate new UUID here - must use the same UUID as in MongoDB
            if (user.getCompanyId() == null) {
                log.error("User {} has no companyId in MongoDB - cannot create company", user.getId());
                return;
            }

            CompanyCreationEvent companyEvent = CompanyCreationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("COMPANY_CREATED")
                    .company(CompanyCreationEvent.CompanyData.builder()
                            .companyId(user.getCompanyId()) // Use from MongoDB
                            .name(user.getCompanyNo()) // companyNo is the tax ID
                            .logoUrl(null) // Will be set later by HR_MANAGER
                            .websiteUrl(null) // Will be set later by HR_MANAGER
                            .description("Company created for HR Manager: " + user.getUsername())
                            .address(null) // Will be set later by HR_MANAGER
                            .size("Unknown") // Will be set later by HR_MANAGER
                            .build())
                    .hrManager(CompanyCreationEvent.HRManagerData.builder()
                            .userId(user.getId())
                            .email(user.getEmail())
                            .username(user.getUsername())
                            .build())
                    .build();

            companyProducer.sendCompanyCreation(companyEvent);

            log.info("Company creation event sent for: {} (HR Manager: {})", user.getCompanyNo(), user.getUsername());
        } catch (Exception e) {
            log.error("Error creating company creation event for user {}: {}", user.getId(), e.getMessage());
        }
    }

    @Override
    public void approveHR(String userId, String approvedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getStatus() != UserStatus.WAIT_APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not waiting for approval");
        }

        UserRole role = UserRole.fromString(user.getRoles().iterator().next());
        if (role != UserRole.HR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not an HR");
        }

        // Update user status to active
        user.setStatus(UserStatus.ACTIVE);
        user.setUpdatedAt(Instant.now());
        User savedUser = userRepository.save(user);

        // Send approval event to user-service
        publishUserApprovalEvent(savedUser, "HR_APPROVED");

        // Send email notification
        sendApprovalNotification(savedUser.getEmail(), savedUser.getUsername(), "HR");

        log.info("HR {} approved by {}", userId, approvedBy);
    }

    @Override
    public void rejectUser(String userId, String rejectedBy, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getStatus() != UserStatus.WAIT_APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not waiting for approval");
        }

        // Update user status to inactive
        user.setStatus(UserStatus.INACTIVE);
        user.setUpdatedAt(Instant.now());
        User savedUser = userRepository.save(user);

        // Send rejection notification
        sendRejectionNotification(savedUser.getEmail(), savedUser.getUsername(), reason);

        log.info("User {} rejected by {} with reason: {}", userId, rejectedBy, reason);
    }

    private void publishUserApprovalEvent(User user, String eventType) {
        UserRegistrationEvent event = UserRegistrationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .timestamp(Instant.now())
                .userData(UserRegistrationEvent.UserData.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .status(user.getStatus().getStatusName())
                        .role(user.getRoles().iterator().next())
                        .build())
                .build();

        userRegistrationProducer.publishUserRegistrationEvent(event);
    }

    private void sendApprovalNotification(String email, String username, String role) {
        boolean isHRManager = role.equals("HR Manager");
        boolean isHR = role.equals("HR");

        notificationProducer.send(NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("EMAIL_NOTIFICATION")
                .recipientEmail(email)
                .recipientRole(role.toUpperCase().replace(" ", "_"))
                .templateType("APPROVAL_GRANTED")
                .subject("Account Approved - WorkFitAI")
                .content("Your " + role
                        + " account has been approved and is now active. You can now log in to WorkFitAI.")
                .metadata(Map.of(
                        "username", username,
                        "role", role,
                        "loginUrl", frontendBaseUrl + "/login",
                        "isHRManager", String.valueOf(isHRManager),
                        "isHR", String.valueOf(isHR),
                        "isCandidate", "false"))
                .build());
    }

    private void sendRejectionNotification(String email, String username, String reason) {
        notificationProducer.send(NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("ACCOUNT_REJECTED")
                .recipientEmail(email)
                .recipientRole("USER")
                .subject("Account Registration Rejected - WorkFitAI")
                .content("Your account registration has been rejected. Reason: "
                        + (reason != null ? reason : "Not specified"))
                .metadata(Map.of("username", username))
                .build());
    }
}