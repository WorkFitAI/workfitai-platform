package org.workfitai.userservice.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.workfitai.userservice.dto.response.UserBaseResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.exception.ApiException;
import org.workfitai.userservice.mapper.AdminMapper;
import org.workfitai.userservice.mapper.CandidateMapper;
import org.workfitai.userservice.mapper.HRMapper;
import org.workfitai.userservice.messaging.SessionInvalidationProducer;
import org.workfitai.userservice.messaging.UserEventPublisher;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock CandidateRepository candidateRepository;
    @Mock HRRepository hrRepository;
    @Mock AdminRepository adminRepository;
    @Mock CandidateMapper candidateMapper;
    @Mock HRMapper hrMapper;
    @Mock AdminMapper adminMapper;
    @Mock UserEventPublisher eventPublisher;
    @Mock SessionInvalidationProducer sessionInvalidationProducer;
    @Mock CandidateService candidateService;
    @Mock HRService hrService;
    @Mock AdminService adminService;

    @InjectMocks
    UserServiceImpl userService;

    private UUID userId;
    private CandidateEntity candidate;
    private HREntity hr;
    private AdminEntity admin;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        candidate = CandidateEntity.builder()
                .userId(userId)
                .username("candidate1")
                .email("candidate@test.com")
                .fullName("Test Candidate")
                .userRole(EUserRole.CANDIDATE)
                .userStatus(EUserStatus.ACTIVE)
                .build();

        hr = HREntity.builder()
                .userId(userId)
                .username("hr1")
                .email("hr@test.com")
                .fullName("Test HR")
                .userRole(EUserRole.HR)
                .userStatus(EUserStatus.ACTIVE)
                .companyId(UUID.randomUUID())
                .companyNo("TAX001")
                .build();

        admin = AdminEntity.builder()
                .userId(userId)
                .username("admin1")
                .email("admin@test.com")
                .fullName("Test Admin")
                .userRole(EUserRole.ADMIN)
                .userStatus(EUserStatus.ACTIVE)
                .build();
    }

    // ---- getCurrentUserProfile ----

    @Test
    void getCurrentUserProfile_candidate_returnsCandidateResponse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(candidate));
        when(candidateRepository.findById(userId)).thenReturn(Optional.of(candidate));
        when(candidateMapper.toResponse(candidate)).thenReturn(mock(org.workfitai.userservice.dto.response.CandidateResponse.class));

        Object result = userService.getCurrentUserProfile(userId);

        assertThat(result).isNotNull();
        verify(candidateMapper).toResponse(candidate);
    }

    @Test
    void getCurrentUserProfile_hr_returnsHRResponse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(hr));
        when(hrRepository.findById(userId)).thenReturn(Optional.of(hr));
        when(hrMapper.toResponse(hr)).thenReturn(mock(org.workfitai.userservice.dto.response.HRResponse.class));

        Object result = userService.getCurrentUserProfile(userId);

        assertThat(result).isNotNull();
        verify(hrMapper).toResponse(hr);
    }

    @Test
    void getCurrentUserProfile_hrManager_returnsHRResponse() {
        hr.setUserRole(EUserRole.HR_MANAGER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(hr));
        when(hrRepository.findById(userId)).thenReturn(Optional.of(hr));
        when(hrMapper.toResponse(hr)).thenReturn(mock(org.workfitai.userservice.dto.response.HRResponse.class));

        userService.getCurrentUserProfile(userId);

        verify(hrMapper).toResponse(hr);
    }

    @Test
    void getCurrentUserProfile_admin_returnsAdminResponse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));
        when(adminRepository.findById(userId)).thenReturn(Optional.of(admin));
        when(adminMapper.toResponse(admin)).thenReturn(mock(org.workfitai.userservice.dto.response.AdminResponse.class));

        userService.getCurrentUserProfile(userId);

        verify(adminMapper).toResponse(admin);
    }

    @Test
    void getCurrentUserProfile_userNotFound_throws() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUserProfile(userId))
                .isInstanceOf(ApiException.class);
    }

    // ---- existsByPhoneNumber ----

    @Test
    void existsByPhoneNumber_nullPhone_returnsFalse() {
        assertThat(userService.existsByPhoneNumber(null)).isFalse();
        verifyNoInteractions(userRepository);
    }

    @Test
    void existsByPhoneNumber_blankPhone_returnsFalse() {
        assertThat(userService.existsByPhoneNumber("   ")).isFalse();
        verifyNoInteractions(userRepository);
    }

    @Test
    void existsByPhoneNumber_validPhone_delegatesToRepo() {
        when(userRepository.existsByPhoneNumber("0901234567")).thenReturn(true);

        assertThat(userService.existsByPhoneNumber("0901234567")).isTrue();
    }

    // ---- getUsersByUsernames ----

    @Test
    void getUsersByUsernames_emptyList_returnsEmpty() {
        assertThat(userService.getUsersByUsernames(List.of())).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    void getUsersByUsernames_validList_mapsAll() {
        when(userRepository.findAllByUsernameIn(List.of("u1", "u2")))
                .thenReturn(List.of(candidate));

        List<UserBaseResponse> result = userService.getUsersByUsernames(List.of("u1", "u2"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("candidate1");
    }

    // ---- getUsersByCompanyId ----

    @Test
    void getUsersByCompanyId_blank_returnsEmpty() {
        assertThat(userService.getUsersByCompanyId("")).isEmpty();
    }

    @Test
    void getUsersByCompanyId_validTaxNo_returnsHrUsers() {
        when(hrRepository.findByCompanyNo("TAX001")).thenReturn(List.of(hr));

        List<UserBaseResponse> result = userService.getUsersByCompanyId("TAX001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("hr1");
    }

    // ---- checkAndReactivateAccount ----

    @Test
    void checkAndReactivateAccount_userNotFound_returnsFalse() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThat(userService.checkAndReactivateAccount("nobody")).isFalse();
    }

    @Test
    void checkAndReactivateAccount_notDeactivated_returnsFalse() {
        candidate.setDeactivatedAt(null);
        when(userRepository.findByUsername("candidate1")).thenReturn(Optional.of(candidate));

        assertThat(userService.checkAndReactivateAccount("candidate1")).isFalse();
    }

    @Test
    void checkAndReactivateAccount_alreadyDeleted_returnsFalse() {
        candidate.setDeactivatedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        candidate.setDeleted(true);
        when(userRepository.findByUsername("candidate1")).thenReturn(Optional.of(candidate));

        assertThat(userService.checkAndReactivateAccount("candidate1")).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void checkAndReactivateAccount_beyond30Days_softDeletes() {
        candidate.setDeactivatedAt(Instant.now().minus(31, ChronoUnit.DAYS));
        when(userRepository.findByUsername("candidate1")).thenReturn(Optional.of(candidate));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = userService.checkAndReactivateAccount("candidate1");

        assertThat(result).isFalse();
        assertThat(candidate.isDeleted()).isTrue();
        assertThat(candidate.getDeletedAt()).isNotNull();
        verify(userRepository).save(candidate);
    }

    @Test
    void checkAndReactivateAccount_within30Days_reactivates() {
        candidate.setDeactivatedAt(Instant.now().minus(5, ChronoUnit.DAYS));
        candidate.setUserStatus(EUserStatus.DEACTIVATED);
        when(userRepository.findByUsername("candidate1")).thenReturn(Optional.of(candidate));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = userService.checkAndReactivateAccount("candidate1");

        assertThat(result).isTrue();
        assertThat(candidate.getDeactivatedAt()).isNull();
        assertThat(candidate.getUserStatus()).isEqualTo(EUserStatus.ACTIVE);
    }

    // ---- searchAllUsers ----

    @Test
    void searchAllUsers_noFilters_returnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(candidate)));

        Page<UserBaseResponse> result = userService.searchAllUsers(null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchAllUsers_withKeyword_appliesFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<UserBaseResponse> result = userService.searchAllUsers("candidate", null, pageable);

        assertThat(result).isEmpty();
    }

    @Test
    void searchAllUsers_withInvalidRole_returnsAll() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(candidate)));

        Page<UserBaseResponse> result = userService.searchAllUsers(null, "INVALID_ROLE", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ---- setUserBlockStatus ----

    @Test
    void setUserBlockStatus_block_updatesStatusAndPublishesEvent() {
        candidate.setBlocked(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(candidate));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.setUserBlockStatus(userId, true);

        assertThat(candidate.isBlocked()).isTrue();
        assertThat(candidate.getUserStatus()).isEqualTo(EUserStatus.SUSPENDED);
        verify(sessionInvalidationProducer).publishSessionInvalidation(eq(userId), any(), eq("BLOCKED"));
        verify(eventPublisher).publishUserBlocked(any());
    }

    @Test
    void setUserBlockStatus_unblock_updatesStatusAndPublishesEvent() {
        candidate.setBlocked(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(candidate));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.setUserBlockStatus(userId, false);

        assertThat(candidate.isBlocked()).isFalse();
        assertThat(candidate.getUserStatus()).isEqualTo(EUserStatus.ACTIVE);
        verify(eventPublisher).publishUserUnblocked(any());
    }

    @Test
    void setUserBlockStatus_userNotFound_throws() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.setUserBlockStatus(userId, true))
                .isInstanceOf(ApiException.class);
    }

    // ---- deleteUser ----

    @Test
    void deleteUser_success_softDeletes() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(candidate));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.deleteUser(userId);

        assertThat(candidate.isDeleted()).isTrue();
        assertThat(candidate.getDeletedAt()).isNotNull();
        assertThat(candidate.getUserStatus()).isEqualTo(EUserStatus.DELETED);
        verify(eventPublisher).publishUserDeleted(any());
    }

    @Test
    void deleteUser_alreadyDeleted_throws() {
        candidate.setDeleted(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already deleted");
    }

    // ---- setUserBlockStatusByUsername ----

    @Test
    void setUserBlockStatusByUsername_selfBlock_throws() {
        String currentUserId = userId.toString();
        when(userRepository.findByUsername("candidate1")).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> userService.setUserBlockStatusByUsername("candidate1", true, currentUserId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot block yourself");
    }

    @Test
    void setUserBlockStatusByUsername_differentUser_delegates() {
        String currentUserId = UUID.randomUUID().toString();
        when(userRepository.findByUsername("candidate1")).thenReturn(Optional.of(candidate));
        when(userRepository.findById(userId)).thenReturn(Optional.of(candidate));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.setUserBlockStatusByUsername("candidate1", true, currentUserId);

        verify(userRepository, atLeastOnce()).save(any());
    }

    // ---- deleteUserByUsername ----

    @Test
    void deleteUserByUsername_selfDelete_throws() {
        String currentUserId = userId.toString();
        when(userRepository.findByUsername("candidate1")).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> userService.deleteUserByUsername("candidate1", currentUserId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot delete yourself");
    }

    // ---- addOAuthProvider ----

    @Test
    void addOAuthProvider_newProvider_addsAndSaves() {
        candidate.setOauthProviders(null);
        when(userRepository.findByUsername("candidate1")).thenReturn(Optional.of(candidate));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.addOAuthProvider("candidate1", "GOOGLE", "g@test.com");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getOauthProviders().toString()).contains("GOOGLE");
    }

    @Test
    void addOAuthProvider_providerAlreadyExists_doesNotSave() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = "[{\"provider\":\"GOOGLE\",\"email\":\"g@test.com\"}]";
        candidate.setOauthProviders(om.readTree(json));
        when(userRepository.findByUsername("candidate1")).thenReturn(Optional.of(candidate));

        userService.addOAuthProvider("candidate1", "GOOGLE", "g@test.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    void addOAuthProvider_userNotFound_throws() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.addOAuthProvider("nobody", "GOOGLE", "g@test.com"))
                .isInstanceOf(ApiException.class);
    }

    // ---- removeOAuthProvider ----

    @Test
    void removeOAuthProvider_existing_removesAndSaves() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = "[{\"provider\":\"GOOGLE\",\"email\":\"g@test.com\"}]";
        candidate.setOauthProviders(om.readTree(json));
        when(userRepository.findByUsername("candidate1")).thenReturn(Optional.of(candidate));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.removeOAuthProvider("candidate1", "GOOGLE");

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getOauthProviders().toString()).doesNotContain("GOOGLE");
    }

    @Test
    void removeOAuthProvider_noProviders_noSave() {
        candidate.setOauthProviders(null);
        when(userRepository.findByUsername("candidate1")).thenReturn(Optional.of(candidate));

        userService.removeOAuthProvider("candidate1", "GOOGLE");

        verify(userRepository, never()).save(any());
    }
}
