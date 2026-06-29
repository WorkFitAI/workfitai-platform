package org.workfitai.userservice.service.impl;

import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.workfitai.userservice.dto.kafka.UserRegistrationEvent;
import org.workfitai.userservice.dto.request.HRCreateRequest;
import org.workfitai.userservice.dto.request.HRUpdateRequest;
import org.workfitai.userservice.dto.response.HRResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.exception.ApiException;
import org.workfitai.userservice.mapper.HRMapper;
import org.workfitai.userservice.messaging.CompanySyncProducer;
import org.workfitai.userservice.messaging.NotificationProducer;
import org.workfitai.userservice.messaging.SessionInvalidationProducer;
import org.workfitai.userservice.messaging.UserEventPublisher;
import org.workfitai.userservice.messaging.UserRegistrationProducer;
import org.workfitai.userservice.model.HREntity;
import org.workfitai.userservice.repository.HRRepository;
import org.workfitai.userservice.security.SecurityContextUtils;
import org.workfitai.userservice.specification.HRSpecification;

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
class HRServiceImplTest {

    @Mock HRRepository hrRepository;
    @Mock HRMapper hrMapper;
    @Mock HRSpecification hrSpecification;
    @Mock Validator validator;
    @Mock CompanySyncProducer companySyncProducer;
    @Mock NotificationProducer notificationProducer;
    @Mock SessionInvalidationProducer sessionInvalidationProducer;
    @Mock UserRegistrationProducer userRegistrationProducer;
    @Mock UserEventPublisher userEventPublisher;

    @InjectMocks
    HRServiceImpl hrService;

    private UUID hrId;
    private HREntity hrEntity;
    private HRResponse hrResponse;
    private MockedStatic<SecurityContextUtils> securityMock;

    @BeforeEach
    void setUp() {
        hrId = UUID.randomUUID();

        hrEntity = HREntity.builder()
                .userId(hrId)
                .email("hr@company.com")
                .username("hr_user")
                .fullName("Test HR")
                .phoneNumber("0900000001")
                .userRole(EUserRole.HR)
                .userStatus(EUserStatus.ACTIVE)
                .companyId(UUID.randomUUID())
                .companyNo("TAX001")
                .companyName("Test Company")
                .build();

        hrResponse = mock(HRResponse.class);

        when(validator.validate(any())).thenReturn(Set.of());

        securityMock = mockStatic(SecurityContextUtils.class);
        securityMock.when(() -> SecurityContextUtils.callerHasRole("HR_MANAGER")).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        securityMock.close();
    }

    // ---- create ----

    @Test
    void create_success_savesAndReturns() {
        HRCreateRequest req = buildCreateRequest();
        when(hrRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(hrMapper.toEntity(req)).thenReturn(hrEntity);
        when(hrRepository.save(hrEntity)).thenReturn(hrEntity);
        when(hrMapper.toResponse(hrEntity)).thenReturn(hrResponse);

        HRResponse result = hrService.create(req);

        assertThat(result).isSameAs(hrResponse);
        verify(hrRepository).save(hrEntity);
    }

    @Test
    void create_emailExists_throws() {
        HRCreateRequest req = buildCreateRequest();
        when(hrRepository.existsByEmail(req.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> hrService.create(req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Email already exists");
    }

    // ---- update ----

    @Test
    void update_asAdmin_updatesAndSaves() {
        HRUpdateRequest req = new HRUpdateRequest();
        when(hrRepository.findById(hrId)).thenReturn(Optional.of(hrEntity));
        when(hrRepository.save(hrEntity)).thenReturn(hrEntity);
        when(hrMapper.toResponse(hrEntity)).thenReturn(hrResponse);

        HRResponse result = hrService.update(hrId, req);

        assertThat(result).isSameAs(hrResponse);
        verify(hrMapper).updateEntityFromUpdateRequest(req, hrEntity);
    }

    @Test
    void update_notFound_throws() {
        when(hrRepository.findById(hrId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hrService.update(hrId, new HRUpdateRequest()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void update_hrManagerDifferentCompany_throws() {
        securityMock.when(() -> SecurityContextUtils.callerHasRole("HR_MANAGER")).thenReturn(true);
        securityMock.when(SecurityContextUtils::currentCallerCompanyNo).thenReturn("OTHER_TAX");
        when(hrRepository.findById(hrId)).thenReturn(Optional.of(hrEntity));

        assertThatThrownBy(() -> hrService.update(hrId, new HRUpdateRequest()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Access denied");
    }

    // ---- delete ----

    @Test
    void delete_success_marksDeletedAndPublishes() {
        when(hrRepository.findById(hrId)).thenReturn(Optional.of(hrEntity));
        when(hrRepository.save(any())).thenReturn(hrEntity);

        hrService.delete(hrId);

        assertThat(hrEntity.getUserStatus()).isEqualTo(EUserStatus.DELETED);
        assertThat(hrEntity.isBlocked()).isTrue();
        verify(sessionInvalidationProducer).publishSessionInvalidation(any(), any(), any());
        verify(userEventPublisher).publishUserDeleted(any());
        verify(notificationProducer).send(any());
    }

    @Test
    void delete_alreadyDeleted_throws() {
        hrEntity.setUserStatus(EUserStatus.DELETED);
        when(hrRepository.findById(hrId)).thenReturn(Optional.of(hrEntity));

        assertThatThrownBy(() -> hrService.delete(hrId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already been terminated");
    }

    // ---- getById ----

    @Test
    void getById_found_returnsResponse() {
        when(hrRepository.findById(hrId)).thenReturn(Optional.of(hrEntity));
        when(hrMapper.toResponse(hrEntity)).thenReturn(hrResponse);

        assertThat(hrService.getById(hrId)).isSameAs(hrResponse);
    }

    @Test
    void getById_notFound_throws() {
        when(hrRepository.findById(hrId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hrService.getById(hrId))
                .isInstanceOf(ApiException.class);
    }

    // ---- search ----

    @Test
    void search_asAdmin_noCompanyFilter() {
        Specification<HREntity> spec = mock(Specification.class);
        when(hrSpecification.filter("kw", null)).thenReturn(spec);
        when(hrRepository.findAll(eq(spec), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(hrEntity)));
        when(hrMapper.toResponse(hrEntity)).thenReturn(hrResponse);

        Page<HRResponse> result = hrService.search("kw", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void search_asHrManager_scopedToCompany() {
        securityMock.when(() -> SecurityContextUtils.callerHasRole("HR_MANAGER")).thenReturn(true);
        securityMock.when(SecurityContextUtils::currentCallerCompanyNo).thenReturn("TAX001");

        Specification<HREntity> spec = mock(Specification.class);
        when(hrSpecification.filter("kw", "TAX001")).thenReturn(spec);
        when(hrRepository.findAll(eq(spec), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(hrEntity)));
        when(hrMapper.toResponse(hrEntity)).thenReturn(hrResponse);

        Page<HRResponse> result = hrService.search("kw", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ---- createFromKafkaEvent (HR) ----

    @Test
    void createFromKafkaEvent_hr_createsWithManagerCompany() {
        HREntity manager = HREntity.builder()
                .userId(UUID.randomUUID())
                .email("manager@company.com")
                .userRole(EUserRole.HR_MANAGER)
                .userStatus(EUserStatus.ACTIVE)
                .companyId(UUID.randomUUID())
                .companyNo("TAX001")
                .companyName("Test Company")
                .build();

        UserRegistrationEvent.UserData userData = buildHrUserData("HR", "manager@company.com");
        when(hrRepository.findByEmail(userData.getEmail())).thenReturn(Optional.empty());
        when(hrRepository.findByUsername(userData.getUsername())).thenReturn(Optional.empty());
        when(hrRepository.existsByPhoneNumber(userData.getPhoneNumber())).thenReturn(false);
        when(hrRepository.findByEmail("manager@company.com")).thenReturn(Optional.of(manager));
        when(hrRepository.save(any())).thenReturn(hrEntity);

        hrService.createFromKafkaEvent(userData);

        verify(hrRepository).save(any());
        verify(userEventPublisher).publishUserCreated(any());
        verify(notificationProducer).send(any());
    }

    @Test
    void createFromKafkaEvent_hrManager_createsWithCompanyFromEvent() {
        UserRegistrationEvent.UserData userData = buildHrManagerUserData();
        when(hrRepository.findByEmail(userData.getEmail())).thenReturn(Optional.empty());
        when(hrRepository.findByUsername(userData.getUsername())).thenReturn(Optional.empty());
        when(hrRepository.existsByPhoneNumber(userData.getPhoneNumber())).thenReturn(false);
        when(hrRepository.existsByCompanyIdAndUserRole(any(), eq(EUserRole.HR_MANAGER))).thenReturn(false);
        when(hrRepository.save(any())).thenReturn(hrEntity);

        hrService.createFromKafkaEvent(userData);

        verify(hrRepository).save(any());
    }

    @Test
    void createFromKafkaEvent_existingHr_updatesIdempotently() {
        UserRegistrationEvent.UserData userData = buildHrUserData("HR", "manager@company.com");
        when(hrRepository.findByEmail(userData.getEmail())).thenReturn(Optional.of(hrEntity));
        when(hrRepository.save(any())).thenReturn(hrEntity);

        hrService.createFromKafkaEvent(userData);

        verify(hrRepository).save(hrEntity);
        verify(userEventPublisher, never()).publishUserCreated(any());
    }

    @Test
    void createFromKafkaEvent_phoneConflict_skips() {
        UserRegistrationEvent.UserData userData = buildHrUserData("HR", "manager@company.com");
        when(hrRepository.findByEmail(userData.getEmail())).thenReturn(Optional.empty());
        when(hrRepository.findByUsername(userData.getUsername())).thenReturn(Optional.empty());
        when(hrRepository.existsByPhoneNumber(userData.getPhoneNumber())).thenReturn(true);

        hrService.createFromKafkaEvent(userData);

        verify(hrRepository, never()).save(any());
    }

    @Test
    void createFromKafkaEvent_companyAlreadyHasManager_throws() {
        UserRegistrationEvent.UserData userData = buildHrManagerUserData();
        when(hrRepository.findByEmail(userData.getEmail())).thenReturn(Optional.empty());
        when(hrRepository.findByUsername(userData.getUsername())).thenReturn(Optional.empty());
        when(hrRepository.existsByPhoneNumber(any())).thenReturn(false);
        when(hrRepository.existsByCompanyIdAndUserRole(any(), eq(EUserRole.HR_MANAGER))).thenReturn(true);

        assertThatThrownBy(() -> hrService.createFromKafkaEvent(userData))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already has an HR manager");
    }

    // ---- approveHrManager ----

    @Test
    void approveHrManager_success_activatesAndNotifies() {
        hrEntity.setUserRole(EUserRole.HR_MANAGER);
        hrEntity.setUserStatus(EUserStatus.WAIT_APPROVED);
        when(hrRepository.findById(hrId)).thenReturn(Optional.of(hrEntity));
        when(hrRepository.save(any())).thenReturn(hrEntity);
        when(hrMapper.toResponse(hrEntity)).thenReturn(hrResponse);

        HRResponse result = hrService.approveHrManager(hrId, "admin1");

        assertThat(result).isSameAs(hrResponse);
        assertThat(hrEntity.getUserStatus()).isEqualTo(EUserStatus.ACTIVE);
        verify(userRegistrationProducer).publishUserRegistrationEvent(any());
        verify(notificationProducer).send(any());
    }

    @Test
    void approveHrManager_wrongRole_throws() {
        hrEntity.setUserRole(EUserRole.HR);
        when(hrRepository.findById(hrId)).thenReturn(Optional.of(hrEntity));

        assertThatThrownBy(() -> hrService.approveHrManager(hrId, "admin1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("HR Manager");
    }

    @Test
    void approveHrManager_alreadyActive_returnsEarly() {
        hrEntity.setUserRole(EUserRole.HR_MANAGER);
        hrEntity.setUserStatus(EUserStatus.ACTIVE);
        when(hrRepository.findById(hrId)).thenReturn(Optional.of(hrEntity));
        when(hrMapper.toResponse(hrEntity)).thenReturn(hrResponse);

        HRResponse result = hrService.approveHrManager(hrId, "admin1");

        assertThat(result).isSameAs(hrResponse);
        verify(hrRepository, never()).save(any());
    }

    // ---- rejectHrManager ----

    @Test
    void rejectHrManager_success_marksRejectedAndNotifies() {
        hrEntity.setUserRole(EUserRole.HR_MANAGER);
        hrEntity.setUserStatus(EUserStatus.WAIT_APPROVED);
        when(hrRepository.findById(hrId)).thenReturn(Optional.of(hrEntity));
        when(hrRepository.save(any())).thenReturn(hrEntity);
        when(hrMapper.toResponse(hrEntity)).thenReturn(hrResponse);

        HRResponse result = hrService.rejectHrManager(hrId, "admin1");

        assertThat(result).isSameAs(hrResponse);
        verify(notificationProducer).send(any());
    }

    @Test
    void rejectHrManager_wrongRole_throws() {
        hrEntity.setUserRole(EUserRole.HR);
        when(hrRepository.findById(hrId)).thenReturn(Optional.of(hrEntity));

        assertThatThrownBy(() -> hrService.rejectHrManager(hrId, "admin1"))
                .isInstanceOf(ApiException.class);
    }

    // ---- helpers ----

    private HRCreateRequest buildCreateRequest() {
        HRCreateRequest req = new HRCreateRequest();
        req.setEmail("new_hr@company.com");
        req.setPhoneNumber("0900000010");
        req.setPassword("pass123");
        req.setFullName("New HR");
        req.setCompanyId(UUID.randomUUID().toString());
        req.setDepartment("IT");
        req.setAddress("123 Main St");
        return req;
    }

    private UserRegistrationEvent.UserData buildHrUserData(String role, String hrManagerEmail) {
        UserRegistrationEvent.UserData d = new UserRegistrationEvent.UserData();
        d.setEmail("hr_kafka@company.com");
        d.setUsername("hr_kafka_user");
        d.setFullName("HR Kafka");
        d.setPhoneNumber("0900000020");
        d.setPasswordHash("$2a$10$hashed");
        d.setRole(role);
        d.setStatus("PENDING");

        UserRegistrationEvent.HrProfile profile = new UserRegistrationEvent.HrProfile();
        profile.setDepartment("IT");
        profile.setHrManagerEmail(hrManagerEmail);
        profile.setAddress("123 Main St");
        d.setHrProfile(profile);
        return d;
    }

    private UserRegistrationEvent.UserData buildHrManagerUserData() {
        UserRegistrationEvent.UserData d = buildHrUserData("HR_MANAGER", null);
        d.getHrProfile().setHrManagerEmail(null);

        UserRegistrationEvent.CompanyData company = new UserRegistrationEvent.CompanyData();
        company.setCompanyId(UUID.randomUUID().toString());
        company.setCompanyNo("TAX001");
        company.setName("Test Company");
        d.setCompany(company);
        return d;
    }
}
