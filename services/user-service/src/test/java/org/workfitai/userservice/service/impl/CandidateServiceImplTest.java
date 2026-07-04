package org.workfitai.userservice.service.impl;

import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.workfitai.userservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.userservice.dto.request.CandidateCreateRequest;
import org.workfitai.userservice.dto.request.CandidateUpdateRequest;
import org.workfitai.userservice.dto.response.CandidateResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.exception.ApiException;
import org.workfitai.userservice.mapper.CandidateMapper;
import org.workfitai.userservice.messaging.UserEventPublisher;
import org.workfitai.userservice.model.CandidateEntity;
import org.workfitai.userservice.repository.CandidateRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CandidateServiceImplTest {

    @Mock CandidateRepository candidateRepository;
    @Mock CandidateMapper candidateMapper;
    @Mock Validator validator;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserEventPublisher userEventPublisher;

    @InjectMocks
    CandidateServiceImpl candidateService;

    private UUID candidateId;
    private CandidateEntity entity;
    private CandidateResponse response;

    @BeforeEach
    void setUp() {
        candidateId = UUID.randomUUID();

        entity = CandidateEntity.builder()
                .userId(candidateId)
                .email("c@test.com")
                .username("cand1")
                .fullName("Test Candidate")
                .phoneNumber("0900000001")
                .userRole(EUserRole.CANDIDATE)
                .userStatus(EUserStatus.ACTIVE)
                .totalExperience(2)
                .build();

        response = mock(CandidateResponse.class);

        // default: no constraint violations
        when(validator.validate(any())).thenReturn(Set.of());
    }

    // ---- create ----

    @Test
    void create_success_savesAndReturnsResponse() {
        CandidateCreateRequest req = buildCreateRequest("c@test.com", "0900000001", 2);
        when(candidateRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(candidateRepository.existsByPhoneNumber(req.getPhoneNumber())).thenReturn(false);
        when(candidateMapper.toEntity(req)).thenReturn(entity);
        when(passwordEncoder.encode(req.getPassword())).thenReturn("hashed");
        when(candidateRepository.save(entity)).thenReturn(entity);
        when(candidateMapper.toResponse(entity)).thenReturn(response);

        CandidateResponse result = candidateService.create(req);

        assertThat(result).isSameAs(response);
        verify(candidateRepository).save(entity);
    }

    @Test
    void create_emailDuplicate_throws() {
        CandidateCreateRequest req = buildCreateRequest("dup@test.com", "0900000002", 2);
        when(candidateRepository.existsByEmail(req.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> candidateService.create(req))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void create_phoneDuplicate_throws() {
        CandidateCreateRequest req = buildCreateRequest("new@test.com", "0900000001", 2);
        when(candidateRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(candidateRepository.existsByPhoneNumber(req.getPhoneNumber())).thenReturn(true);

        assertThatThrownBy(() -> candidateService.create(req))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void create_invalidExperience_throws() {
        CandidateCreateRequest req = buildCreateRequest("e@test.com", "0900000003", 99);
        when(candidateRepository.existsByEmail(any())).thenReturn(false);

        assertThatThrownBy(() -> candidateService.create(req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("experience");
    }

    @Test
    void create_negativeExperience_throws() {
        CandidateCreateRequest req = buildCreateRequest("e@test.com", "0900000003", -1);

        assertThatThrownBy(() -> candidateService.create(req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("experience");
    }

    @Test
    void create_constraintViolation_throwsValidationError() {
        CandidateCreateRequest req = buildCreateRequest("bad@test.com", "0900000003", 2);
        @SuppressWarnings("unchecked")
        ConstraintViolation<CandidateCreateRequest> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("email");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be valid");
        when(validator.validate(req)).thenReturn(Set.of(violation));

        assertThatThrownBy(() -> candidateService.create(req))
                .isInstanceOf(ApiException.class);

        verify(candidateRepository, never()).existsByEmail(any());
    }

    // ---- update ----

    @Test
    void update_success_updatesAndReturns() {
        CandidateUpdateRequest req = new CandidateUpdateRequest();
        req.setEmail("updated@test.com");
        req.setTotalExperience(3);

        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(entity));
        when(candidateRepository.existsByEmail("updated@test.com")).thenReturn(false);
        when(candidateRepository.save(entity)).thenReturn(entity);
        when(candidateMapper.toResponse(entity)).thenReturn(response);

        CandidateResponse result = candidateService.update(candidateId, req);

        assertThat(result).isSameAs(response);
    }

    @Test
    void update_notFound_throws() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.update(candidateId, new CandidateUpdateRequest()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void update_negativeExperience_throws() {
        CandidateUpdateRequest req = new CandidateUpdateRequest();
        req.setTotalExperience(-1);
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> candidateService.update(candidateId, req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("experience");
    }

    @Test
    void update_sameEmailDifferentCase_doesNotCheckEmailDuplicate() {
        CandidateUpdateRequest req = new CandidateUpdateRequest();
        req.setEmail("C@TEST.COM");
        req.setPhoneNumber(entity.getPhoneNumber());
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(entity));
        when(candidateRepository.save(entity)).thenReturn(entity);
        when(candidateMapper.toResponse(entity)).thenReturn(response);

        assertThat(candidateService.update(candidateId, req)).isSameAs(response);

        verify(candidateRepository, never()).existsByEmail(any());
        verify(candidateRepository, never()).existsByPhoneNumber(any());
    }

    @Test
    void update_phoneDuplicate_throws() {
        CandidateUpdateRequest req = new CandidateUpdateRequest();
        req.setPhoneNumber("0900000099");
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(entity));
        when(candidateRepository.existsByPhoneNumber(req.getPhoneNumber())).thenReturn(true);

        assertThatThrownBy(() -> candidateService.update(candidateId, req))
                .isInstanceOf(ApiException.class);
    }

    // ---- delete ----

    @Test
    void delete_success_removesEntity() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(entity));

        candidateService.delete(candidateId);

        verify(candidateRepository).delete(entity);
    }

    @Test
    void delete_notFound_throws() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.delete(candidateId))
                .isInstanceOf(ApiException.class);
    }

    // ---- getById ----

    @Test
    void getById_found_returnsResponse() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(entity));
        when(candidateMapper.toResponse(entity)).thenReturn(response);

        assertThat(candidateService.getById(candidateId)).isSameAs(response);
    }

    @Test
    void getById_notFound_throws() {
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.getById(candidateId))
                .isInstanceOf(ApiException.class);
    }

    // ---- search ----

    @Test
    void search_delegatesToRepo() {
        when(candidateRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(candidateMapper.toResponse(entity)).thenReturn(response);

        Page<CandidateResponse> result = candidateService.search("keyword", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ---- filter ----

    @Test
    void filter_minGreaterThanMax_throws() {
        assertThatThrownBy(() -> candidateService.filter(null, 5, 2, PageRequest.of(0, 10)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Minimum experience");
    }

    @Test
    void filter_validRange_delegatesToRepo() {
        when(candidateRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(candidateMapper.toResponse(entity)).thenReturn(response);

        Page<CandidateResponse> result = candidateService.filter(null, 1, 5, PageRequest.of(0, 10));

        assertThat(result).isNotNull();
    }

    // ---- getExperienceStats ----

    @Test
    void getExperienceStats_returnsMap() {
        when(candidateRepository.countByExperienceRange())
                .thenReturn(List.of(new Object[]{"Junior", 10L}, new Object[]{"Senior", 5L}));

        var stats = candidateService.getExperienceStats();

        assertThat(stats).containsKeys("Junior", "Senior");
        assertThat(stats.get("Junior")).isEqualTo(10L);
    }

    // ---- createFromKafkaEvent ----

    @Test
    void createFromKafkaEvent_newCandidate_savesAndPublishes() {
        UserRegistrationEvent.UserData userData = buildUserData("CANDIDATE", true);

        when(candidateRepository.findByEmail(userData.getEmail())).thenReturn(Optional.empty());
        when(candidateRepository.findByUsername(userData.getUsername())).thenReturn(Optional.empty());
        when(candidateRepository.existsByPhoneNumber(any())).thenReturn(false);
        when(candidateRepository.save(any())).thenReturn(entity);

        candidateService.createFromKafkaEvent(userData);

        verify(candidateRepository).save(any());
        verify(userEventPublisher).publishUserCreated(any());
    }

    @Test
    void createFromKafkaEvent_existingByEmail_updatesIdempotently() {
        UserRegistrationEvent.UserData userData = buildUserData("CANDIDATE", true);

        when(candidateRepository.findByEmail(userData.getEmail())).thenReturn(Optional.of(entity));
        when(candidateRepository.save(any())).thenReturn(entity);

        candidateService.createFromKafkaEvent(userData);

        verify(candidateRepository).save(entity);
        verify(userEventPublisher, never()).publishUserCreated(any());
    }

    @Test
    void createFromKafkaEvent_oAuthRegistration_noPhoneRequired() {
        UserRegistrationEvent.UserData userData = buildUserData("CANDIDATE", false);
        userData.setPhoneNumber(null);

        when(candidateRepository.findByEmail(userData.getEmail())).thenReturn(Optional.empty());
        when(candidateRepository.findByUsername(userData.getUsername())).thenReturn(Optional.empty());
        when(candidateRepository.save(any())).thenReturn(entity);

        candidateService.createFromKafkaEvent(userData);

        verify(candidateRepository).save(any());
    }

    @Test
    void createFromKafkaEvent_nullRoleAndStatus_defaultsToActiveCandidate() {
        UserRegistrationEvent.UserData userData = buildUserData(null, false);
        userData.setStatus(null);
        userData.setPhoneNumber(null);

        when(candidateRepository.findByEmail(userData.getEmail())).thenReturn(Optional.empty());
        when(candidateRepository.findByUsername(userData.getUsername())).thenReturn(Optional.empty());
        when(candidateRepository.save(any(CandidateEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        candidateService.createFromKafkaEvent(userData);

        verify(candidateRepository).save(argThat(candidate ->
                candidate.getUserRole() == EUserRole.CANDIDATE
                        && candidate.getUserStatus() == EUserStatus.ACTIVE
                        && candidate.getPhoneNumber() == null));
    }

    @Test
    void createFromKafkaEvent_phoneConflict_skipsCreation() {
        UserRegistrationEvent.UserData userData = buildUserData("CANDIDATE", true);

        when(candidateRepository.findByEmail(userData.getEmail())).thenReturn(Optional.empty());
        when(candidateRepository.findByUsername(userData.getUsername())).thenReturn(Optional.empty());
        when(candidateRepository.existsByPhoneNumber(userData.getPhoneNumber())).thenReturn(true);

        candidateService.createFromKafkaEvent(userData);

        verify(candidateRepository, never()).save(any());
    }

    @Test
    void createFromKafkaEvent_wrongRole_throws() {
        UserRegistrationEvent.UserData userData = buildUserData("HR", true);

        assertThatThrownBy(() -> candidateService.createFromKafkaEvent(userData))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void createFromKafkaEvent_traditionalRegistrationMissingPhone_throws() {
        UserRegistrationEvent.UserData userData = buildUserData("CANDIDATE", true);
        userData.setPhoneNumber(" ");

        when(candidateRepository.findByEmail(userData.getEmail())).thenReturn(Optional.empty());
        when(candidateRepository.findByUsername(userData.getUsername())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.createFromKafkaEvent(userData))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Phone number");
    }

    // ---- updateStatus ----

    @Test
    void updateStatus_success_savesNewStatus() {
        when(candidateRepository.findByEmail("c@test.com")).thenReturn(Optional.of(entity));
        when(candidateRepository.save(entity)).thenReturn(entity);

        candidateService.updateStatus("c@test.com", EUserStatus.SUSPENDED);

        assertThat(entity.getUserStatus()).isEqualTo(EUserStatus.SUSPENDED);
        verify(candidateRepository).save(entity);
    }

    @Test
    void updateStatus_notFound_throws() {
        when(candidateRepository.findByEmail("x@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.updateStatus("x@test.com", EUserStatus.ACTIVE))
                .isInstanceOf(ApiException.class);
    }

    // ---- helpers ----

    private CandidateCreateRequest buildCreateRequest(String email, String phone, int exp) {
        CandidateCreateRequest req = new CandidateCreateRequest();
        req.setEmail(email);
        req.setPhoneNumber(phone);
        req.setPassword("pass123");
        req.setFullName("Test");
        req.setTotalExperience(exp);
        return req;
    }

    private UserRegistrationEvent.UserData buildUserData(String role, boolean withPassword) {
        UserRegistrationEvent.UserData d = new UserRegistrationEvent.UserData();
        d.setEmail("k@test.com");
        d.setUsername("kafka_user");
        d.setFullName("Kafka User");
        d.setPhoneNumber("0900000099");
        d.setRole(role);
        d.setStatus("ACTIVE");
        if (withPassword) {
            d.setPasswordHash("$2a$10$hashed");
        }
        return d;
    }
}
