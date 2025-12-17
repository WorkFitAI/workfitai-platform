package org.workfitai.userservice.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
import org.workfitai.userservice.model.AdminEntity;
import org.workfitai.userservice.model.CandidateEntity;
import org.workfitai.userservice.model.HREntity;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.repository.AdminRepository;
import org.workfitai.userservice.repository.CandidateRepository;
import org.workfitai.userservice.repository.HRRepository;
import org.workfitai.userservice.repository.UserRepository;
import org.workfitai.userservice.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
        return UserBaseResponse.builder()
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
                .isDeleted(user.isDeleted())
                .build();
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
}
