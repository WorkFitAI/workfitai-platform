package org.workfitai.userservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.userservice.constants.Messages;
import org.workfitai.userservice.dto.response.UserBaseResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.exceptions.ApiException;
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
import org.workfitai.userservice.services.UserService;

import java.util.UUID;

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

    private UserBaseResponse mapToBaseResponse(UserEntity user) {
        return UserBaseResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .userRole(user.getUserRole())
                .userStatus(user.getUserStatus())
                .lastLogin(user.getLastLogin())
                .createdBy(user.getCreatedBy())
                .createdDate(user.getCreatedDate())
                .lastModifiedBy(user.getLastModifiedBy())
                .lastModifiedDate(user.getLastModifiedDate())
                .isDeleted(user.isDeleted())
                .build();
    }
}
