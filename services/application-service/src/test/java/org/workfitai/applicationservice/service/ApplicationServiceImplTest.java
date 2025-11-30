package org.workfitai.applicationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.workfitai.applicationservice.client.CvServiceClient;
import org.workfitai.applicationservice.client.JobServiceClient;
import org.workfitai.applicationservice.client.dto.CompanyDTO;
import org.workfitai.applicationservice.client.dto.CvDTO;
import org.workfitai.applicationservice.client.dto.JobDTO;
import org.workfitai.applicationservice.client.dto.JobStatus;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.exception.ApplicationConflictException;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.service.impl.ApplicationServiceImpl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for ApplicationServiceImpl.
 * 
 * Tests business logic in isolation using mocks.
 * No actual database or external service calls.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicationService Unit Tests")
class ApplicationServiceImplTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationMapper applicationMapper;

    @Mock
    private JobServiceClient jobServiceClient;

    @Mock
    private CvServiceClient cvServiceClient;

    @InjectMocks
    private ApplicationServiceImpl applicationService;

    // Test data
    private static final String USER_ID = "user-123";
    private static final String JOB_ID = UUID.randomUUID().toString();
    private static final String CV_ID = UUID.randomUUID().toString();
    private static final String APP_ID = "app-123";

    private CreateApplicationRequest validRequest;
    private Application savedApplication;
    private ApplicationResponse applicationResponse;
    private JobDTO publishedJob;
    private CvDTO userCV;

    @BeforeEach
    void setUp() {
        // Setup valid request
        validRequest = CreateApplicationRequest.builder()
                .jobId(JOB_ID)
                .cvId(CV_ID)
                .note("I'm very interested in this position")
                .build();

        // Setup saved application entity
        savedApplication = Application.builder()
                .id(APP_ID)
                .userId(USER_ID)
                .jobId(JOB_ID)
                .cvId(CV_ID)
                .status(ApplicationStatus.APPLIED)
                .note("I'm very interested in this position")
                .createdAt(Instant.now())
                .build();

        // Setup application response
        applicationResponse = ApplicationResponse.builder()
                .id(APP_ID)
                .userId(USER_ID)
                .jobId(JOB_ID)
                .cvId(CV_ID)
                .status(ApplicationStatus.APPLIED)
                .note("I'm very interested in this position")
                .build();

        // Setup published job
        publishedJob = JobDTO.builder()
                .jobId(JOB_ID)
                .title("Senior Java Developer")
                .status(JobStatus.PUBLISHED)
                .company(CompanyDTO.builder()
                        .companyId("company-1")
                        .name("TechCorp Inc")
                        .build())
                .build();

        // Setup user's CV
        userCV = CvDTO.builder()
                .cvId(CV_ID)
                .headline("Experienced Java Developer")
                .belongTo(USER_ID) // CV belongs to the applying user
                .isExist(true)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 CREATE APPLICATION TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createApplication()")
    class CreateApplicationTests {

        @Test
        @DisplayName("Should create application successfully when all validations pass")
        void shouldCreateApplicationSuccessfully() {
            // Given: All validations pass
            given(applicationRepository.existsByUserIdAndJobId(USER_ID, JOB_ID)).willReturn(false);
            given(jobServiceClient.getJobById(JOB_ID)).willReturn(RestResponse.success(publishedJob));
            given(cvServiceClient.getCvById(CV_ID)).willReturn(RestResponse.success(userCV));
            given(applicationMapper.toEntity(validRequest)).willReturn(new Application());
            given(applicationRepository.save(any(Application.class))).willReturn(savedApplication);
            given(applicationMapper.toResponse(savedApplication)).willReturn(applicationResponse);

            // When: Create application
            ApplicationResponse result = applicationService.createApplication(validRequest, USER_ID);

            // Then: Application is created
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(APP_ID);
            assertThat(result.getStatus()).isEqualTo(ApplicationStatus.APPLIED);

            // Verify interactions
            then(applicationRepository).should().existsByUserIdAndJobId(USER_ID, JOB_ID);
            then(applicationRepository).should().save(any(Application.class));
        }

        @Test
        @DisplayName("Should throw ApplicationConflictException when user already applied")
        void shouldThrowConflictWhenAlreadyApplied() {
            // Given: User has already applied
            given(applicationRepository.existsByUserIdAndJobId(USER_ID, JOB_ID)).willReturn(true);

            // When/Then: Should throw conflict exception
            assertThatThrownBy(() -> applicationService.createApplication(validRequest, USER_ID))
                    .isInstanceOf(ApplicationConflictException.class)
                    .hasMessageContaining("already applied");

            // Verify no save attempt
            then(applicationRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("Should throw ForbiddenException when job is not PUBLISHED")
        void shouldThrowForbiddenWhenJobNotPublished() {
            // Given: Job is in DRAFT status
            JobDTO draftJob = JobDTO.builder()
                    .jobId(JOB_ID)
                    .status(JobStatus.DRAFT)
                    .build();

            given(applicationRepository.existsByUserIdAndJobId(USER_ID, JOB_ID)).willReturn(false);
            given(jobServiceClient.getJobById(JOB_ID)).willReturn(RestResponse.success(draftJob));

            // When/Then: Should throw forbidden exception
            assertThatThrownBy(() -> applicationService.createApplication(validRequest, USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("not published");
        }

        @Test
        @DisplayName("Should throw ForbiddenException when CV doesn't belong to user")
        void shouldThrowForbiddenWhenCvNotOwned() {
            // Given: CV belongs to another user
            CvDTO otherUserCV = CvDTO.builder()
                    .cvId(CV_ID)
                    .belongTo("other-user-456") // Different user
                    .build();

            given(applicationRepository.existsByUserIdAndJobId(USER_ID, JOB_ID)).willReturn(false);
            given(jobServiceClient.getJobById(JOB_ID)).willReturn(RestResponse.success(publishedJob));
            given(cvServiceClient.getCvById(CV_ID)).willReturn(RestResponse.success(otherUserCV));

            // When/Then: Should throw forbidden exception
            assertThatThrownBy(() -> applicationService.createApplication(validRequest, USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("CV does not belong");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 GET APPLICATION TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getApplicationById()")
    class GetApplicationByIdTests {

        @Test
        @DisplayName("Should return application when found")
        void shouldReturnApplicationWhenFound() {
            // Given
            given(applicationRepository.findById(APP_ID)).willReturn(Optional.of(savedApplication));
            given(applicationMapper.toResponse(savedApplication)).willReturn(applicationResponse);
            // Mock external calls for enrichment (may fail silently)
            given(jobServiceClient.getJobById(JOB_ID)).willReturn(RestResponse.success(publishedJob));
            given(cvServiceClient.getCvById(CV_ID)).willReturn(RestResponse.success(userCV));

            // When
            ApplicationResponse result = applicationService.getApplicationById(APP_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(APP_ID);
        }

        @Test
        @DisplayName("Should throw NotFoundException when application doesn't exist")
        void shouldThrowNotFoundWhenMissing() {
            // Given
            given(applicationRepository.findById(APP_ID)).willReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> applicationService.getApplicationById(APP_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not found");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 GET MY APPLICATIONS TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getMyApplications()")
    class GetMyApplicationsTests {

        @Test
        @DisplayName("Should return paginated applications for user")
        void shouldReturnPaginatedApplications() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Application> page = new PageImpl<>(List.of(savedApplication), pageable, 1);

            given(applicationRepository.findByUserId(USER_ID, pageable)).willReturn(page);
            given(applicationMapper.toResponse(any(Application.class))).willReturn(applicationResponse);
            // Mock enrichment calls
            given(jobServiceClient.getJobById(anyString())).willReturn(RestResponse.success(publishedJob));
            given(cvServiceClient.getCvById(anyString())).willReturn(RestResponse.success(userCV));

            // When
            ResultPaginationDTO<ApplicationResponse> result = applicationService.getMyApplications(USER_ID, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getMeta().getTotalElements()).isEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 UPDATE STATUS TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatusTests {

        @Test
        @DisplayName("Should update status successfully")
        void shouldUpdateStatusSuccessfully() {
            // Given
            given(applicationRepository.findById(APP_ID)).willReturn(Optional.of(savedApplication));
            given(applicationRepository.save(any(Application.class))).willReturn(savedApplication);
            given(applicationMapper.toResponse(any())).willReturn(applicationResponse);
            given(jobServiceClient.getJobById(anyString())).willReturn(RestResponse.success(publishedJob));
            given(cvServiceClient.getCvById(anyString())).willReturn(RestResponse.success(userCV));

            // When
            ApplicationResponse result = applicationService.updateStatus(APP_ID, ApplicationStatus.REVIEWING);

            // Then
            assertThat(result).isNotNull();
            then(applicationRepository).should().save(any(Application.class));
        }

        @Test
        @DisplayName("Should throw NotFoundException when application doesn't exist")
        void shouldThrowNotFoundWhenUpdatingNonexistent() {
            // Given
            given(applicationRepository.findById(APP_ID)).willReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> applicationService.updateStatus(APP_ID, ApplicationStatus.REVIEWING))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 WITHDRAW APPLICATION TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("withdrawApplication()")
    class WithdrawApplicationTests {

        @Test
        @DisplayName("Should withdraw application when user is owner")
        void shouldWithdrawWhenOwner() {
            // Given
            given(applicationRepository.findById(APP_ID)).willReturn(Optional.of(savedApplication));

            // When
            applicationService.withdrawApplication(APP_ID, USER_ID);

            // Then
            then(applicationRepository).should().delete(savedApplication);
        }

        @Test
        @DisplayName("Should throw ForbiddenException when user is not owner")
        void shouldThrowForbiddenWhenNotOwner() {
            // Given
            given(applicationRepository.findById(APP_ID)).willReturn(Optional.of(savedApplication));

            // When/Then
            assertThatThrownBy(() -> applicationService.withdrawApplication(APP_ID, "other-user"))
                    .isInstanceOf(ForbiddenException.class);

            then(applicationRepository).should(never()).delete(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 STATISTICS TESTS
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Statistics Methods")
    class StatisticsTests {

        @Test
        @DisplayName("Should return correct count by user")
        void shouldReturnCountByUser() {
            given(applicationRepository.countByUserId(USER_ID)).willReturn(5L);

            long count = applicationService.countByUser(USER_ID);

            assertThat(count).isEqualTo(5L);
        }

        @Test
        @DisplayName("Should return correct count by job")
        void shouldReturnCountByJob() {
            given(applicationRepository.countByJobId(JOB_ID)).willReturn(10L);

            long count = applicationService.countByJob(JOB_ID);

            assertThat(count).isEqualTo(10L);
        }

        @Test
        @DisplayName("Should return true when user has applied to job")
        void shouldReturnTrueWhenApplied() {
            given(applicationRepository.existsByUserIdAndJobId(USER_ID, JOB_ID)).willReturn(true);

            boolean hasApplied = applicationService.hasUserAppliedToJob(USER_ID, JOB_ID);

            assertThat(hasApplied).isTrue();
        }
    }
}
