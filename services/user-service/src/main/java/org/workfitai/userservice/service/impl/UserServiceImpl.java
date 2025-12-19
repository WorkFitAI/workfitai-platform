package org.workfitai.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.userservice.constants.Messages;
import org.workfitai.userservice.dto.response.UserBaseResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.exception.ApiException;
import org.workfitai.userservice.mapper.AdminMapper;
import org.workfitai.userservice.mapper.CandidateMapper;
import org.workfitai.userservice.mapper.HRMapper;
import org.workfitai.userservice.messaging.UserEventPublisher;
import org.workfitai.userservice.messaging.SessionInvalidationProducer;
import org.workfitai.userservice.model.AdminEntity;
import org.workfitai.userservice.model.CandidateEntity;
import org.workfitai.userservice.model.HREntity;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.repository.AdminRepository;
import org.workfitai.userservice.repository.CandidateRepository;
import org.workfitai.userservice.repository.HRRepository;
import org.workfitai.userservice.repository.UserRepository;
import org.workfitai.userservice.service.AdminService;
import org.workfitai.userservice.service.CandidateService;
import org.workfitai.userservice.service.HRService;
import org.workfitai.userservice.service.UserService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final HRRepository hrRepository;
    private final AdminRepository adminRepository;
    private final CandidateMapper candidateMapper;
    private final HRMapper hrMapper;
    private final AdminMapper adminMapper;
    private final UserEventPublisher eventPublisher;
    private final SessionInvalidationProducer sessionInvalidationProducer;
    private final CandidateService candidateService;
    private final HRService hrService;
    private final AdminService adminService;

    @Override
    public Object getCurrentUserProfile(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(Messages.User.NOT_FOUND));

        EUserRole role = user.getUserRole();
        log.debug("Getting profile for user {} with role {}", userId, role);

        return switch (role) {
            case CANDIDATE -> {
                CandidateEntity candidate = candidateRepository.findById(userId)
                        .orElseThrow(() -> ApiException.notFound(Messages.Profile.PROFILE_NOT_FOUND));
                yield candidateMapper.toResponse(candidate);
            }
            case HR, HR_MANAGER -> {
                HREntity hr = hrRepository.findById(userId)
                        .orElseThrow(() -> ApiException.notFound(Messages.Profile.PROFILE_NOT_FOUND));
                yield hrMapper.toResponse(hr);
            }
            case ADMIN -> {
                AdminEntity admin = adminRepository.findById(userId)
                        .orElseThrow(() -> ApiException.notFound(Messages.Profile.PROFILE_NOT_FOUND));
                yield adminMapper.toResponse(admin);
            }
        };
    }

    @Override
    public Object getCurrentUserProfileByUsername(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound(Messages.User.NOT_FOUND));

        UUID userId = user.getId();
        EUserRole role = user.getUserRole();
        log.debug("Getting profile for user {} with role {}", username, role);

        return switch (role) {
            case CANDIDATE -> {
                CandidateEntity candidate = candidateRepository.findById(userId)
                        .orElseThrow(() -> ApiException.notFound(Messages.Profile.PROFILE_NOT_FOUND));
                yield candidateMapper.toResponse(candidate);
            }
            case HR, HR_MANAGER -> {
                HREntity hr = hrRepository.findById(userId)
                        .orElseThrow(() -> ApiException.notFound(Messages.Profile.PROFILE_NOT_FOUND));
                yield hrMapper.toResponse(hr);
            }
            case ADMIN -> {
                AdminEntity admin = adminRepository.findById(userId)
                        .orElseThrow(() -> ApiException.notFound(Messages.Profile.PROFILE_NOT_FOUND));
                yield adminMapper.toResponse(admin);
            }
        };
    }

    @Override
    public UserBaseResponse getByEmail(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound(Messages.User.NOT_FOUND));
        return mapToBaseResponse(user);
    }

    @Override
    public UserBaseResponse getByUsername(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound(Messages.User.NOT_FOUND));
        return mapToBaseResponse(user);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return false;
        }
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    public UUID findUserIdByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(UserEntity::getId)
                .orElseThrow(() -> ApiException.notFound(Messages.User.NOT_FOUND));
    }

    @Override
    public List<UserBaseResponse> getUsersByUsernames(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserEntity> users = userRepository.findAllByUsernameIn(usernames);
        return users.stream()
                .map(this::mapToBaseResponse)
                .collect(Collectors.toList());
    }

    private UserBaseResponse mapToBaseResponse(UserEntity user) {
        UserBaseResponse.UserBaseResponseBuilder builder = UserBaseResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .userRole(user.getUserRole())
                .userStatus(user.getUserStatus())
                // lastLogin removed - tracked by auth-service
                .createdBy(user.getCreatedBy())
                .createdDate(user.getCreatedDate())
                .lastModifiedBy(user.getLastModifiedBy())
                .lastModifiedDate(user.getLastModifiedDate())
                .isDeleted(user.isDeleted());

        // Add company information for HR and HR_MANAGER roles
        if (user.getUserRole() == EUserRole.HR || user.getUserRole() == EUserRole.HR_MANAGER) {
            hrRepository.findById(user.getUserId()).ifPresent(hr -> {
                builder.companyId(hr.getCompanyId())
                        .companyName(hr.getCompanyName())
                        .companyNo(hr.getCompanyNo())
                        .department(hr.getDepartment())
                        .address(hr.getAddress());
            });
        }

        return builder.build();
    }

    @Override
    @Transactional
    public boolean checkAndReactivateAccount(String username) {
        UserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getDeactivatedAt() == null) {
            return false; // Not deactivated or not found
        }

        // Check if account is already marked as deleted
        if (user.isDeleted() || user.getDeletedAt() != null) {
            log.warn("Account {} is already deleted, cannot reactivate", username);
            return false;
        }

        // Check if deactivation is within 30 days
        Instant now = Instant.now();
        long daysSinceDeactivation = ChronoUnit.DAYS.between(user.getDeactivatedAt(), now);

        if (daysSinceDeactivation > 30) {
            // Mark as deleted (soft delete)
            user.setDeleted(true);
            user.setDeletedAt(now);
            userRepository.save(user);
            log.info("Account {} deactivated for more than 30 days, marked as deleted", username);
            return false;
        }

        // Auto-reactivate account
        user.setDeactivatedAt(null);
        user.setDeactivationReason(null);
        user.setDeletionDate(null);
        user.setUserStatus(EUserStatus.ACTIVE);
        userRepository.save(user);

        log.info("Account {} auto-reactivated on login (deactivated for {} days)", username, daysSinceDeactivation);
        return true;
    }

    @Override
    public Page<UserBaseResponse> searchAllUsers(String keyword, String role, Pageable pageable) {
        Specification<UserEntity> spec = (root, query, cb) -> cb.conjunction();

        // Filter by keyword (username, email, fullName)
        if (keyword != null && !keyword.trim().isEmpty()) {
            String searchTerm = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), searchTerm),
                    cb.like(cb.lower(root.get("email")), searchTerm),
                    cb.like(cb.lower(root.get("fullName")), searchTerm)));
        }

        // Filter by role
        if (role != null && !role.trim().isEmpty()) {
            try {
                EUserRole userRole = EUserRole.valueOf(role.toUpperCase());
                spec = spec.and((root, query, cb) -> cb.equal(root.get("userRole"), userRole));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid role filter: {}", role);
            }
        }

        // Exclude deleted users
        spec = spec.and((root, query, cb) -> cb.equal(root.get("isDeleted"), false));

        Page<UserEntity> userPage = userRepository.findAll(spec, pageable);

        List<UserBaseResponse> responses = userPage.getContent().stream()
                .map(user -> UserBaseResponse.builder()
                        .userId(user.getUserId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .phoneNumber(user.getPhoneNumber())
                        .userRole(user.getUserRole())
                        .userStatus(user.getUserStatus())
                        .createdDate(user.getCreatedDate())
                        .build())
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, userPage.getTotalElements());
    }

    @Override
    public UserBaseResponse getByUserId(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        return mapToBaseResponse(user);
    }

    @Override
    public UserBaseResponse getUserByUsername(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
        return mapToBaseResponse(user);
    }

    /*
     * // TODO: Requires getByUserId() methods in CandidateService, HRService,
     * AdminService
     *
     * @Override
     * public Object getFullUserProfile(UUID userId) {
     * UserEntity user = userRepository.findById(userId)
     * .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));
     *
     * // Delegate to role-specific service
     * switch (user.getUserRole()) {
     * case CANDIDATE:
     * return candidateService.getByUserId(userId);
     *
     * case HR:
     * case HR_MANAGER:
     * return hrService.getByUserId(userId);
     *
     * case ADMIN:
     * return adminService.getByUserId(userId);
     *
     * default:
     * throw new ApiException("Unknown user role: " + user.getUserRole(),
     * HttpStatus.BAD_REQUEST);
     * }
     * }
     */

    @Override
    @Transactional
    public void setUserBlockStatus(UUID userId, boolean blocked) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(Messages.User.NOT_FOUND));

        boolean wasBlocked = user.isBlocked();
        user.setBlocked(blocked);

        if (blocked) {
            user.setUserStatus(EUserStatus.SUSPENDED);
            log.info("User {} blocked by admin", user.getUsername());

            // Invalidate all sessions when blocking user
            sessionInvalidationProducer.publishSessionInvalidation(
                    user.getUserId(),
                    user.getUsername(),
                    "BLOCKED");
        } else {
            user.setUserStatus(EUserStatus.ACTIVE);
            log.info("User {} unblocked by admin", user.getUsername());
        }

        user = userRepository.save(user);

        // Publish event to Kafka for Elasticsearch sync
        if (blocked && !wasBlocked) {
            eventPublisher.publishUserBlocked(user);
        } else if (!blocked && wasBlocked) {
            eventPublisher.publishUserUnblocked(user);
        }
    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(Messages.User.NOT_FOUND));

        if (user.isDeleted()) {
            throw new ApiException("User already deleted", HttpStatus.CONFLICT);
        }

        // Invalidate all sessions when deleting user
        sessionInvalidationProducer.publishSessionInvalidation(
                user.getUserId(),
                user.getUsername(),
                "DELETED");

        // Soft delete
        user.setDeleted(true);
        user.setDeletedAt(Instant.now());
        user.setUserStatus(EUserStatus.DELETED);

        user = userRepository.save(user);
        log.info("User {} soft deleted by admin", user.getUsername());

        // Publish event to Kafka for Elasticsearch sync
        eventPublisher.publishUserDeleted(user);
    }

    @Override
    @Transactional
    public void setUserBlockStatusByUsername(String username, boolean blocked, String currentUserId) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("User not found with username: " + username));

        // Prevent self-blocking
        if (user.getUserId().toString().equals(currentUserId)) {
            throw new ApiException("You cannot block yourself", HttpStatus.BAD_REQUEST);
        }

        setUserBlockStatus(user.getUserId(), blocked);
    }

    @Override
    @Transactional
    public void deleteUserByUsername(String username, String currentUserId) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("User not found with username: " + username));

        // Prevent self-deletion
        if (user.getUserId().toString().equals(currentUserId)) {
            throw new ApiException("You cannot delete yourself", HttpStatus.BAD_REQUEST);
        }

        deleteUser(user.getUserId());
    }
}
