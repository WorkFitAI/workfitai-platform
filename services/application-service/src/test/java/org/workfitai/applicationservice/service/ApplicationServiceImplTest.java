package org.workfitai.applicationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.workfitai.applicationservice.dto.kafka.ApplicationCreatedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationStatusChangedEvent;
import org.workfitai.applicationservice.dto.kafka.ApplicationWithdrawnEvent;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.exception.ApplicationConflictException;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.messaging.ApplicationEventProducer;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicationService Unit Tests")
class ApplicationServiceImplTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationMapper applicationMapper;

    @Mock
    private ApplicationEventProducer eventProducer;

    @InjectMocks
    private ApplicationServiceImpl applicationService;

    @Captor
    private ArgumentCaptor<ApplicationCreatedEvent> createdEventCaptor;

    @Captor
    private ArgumentCaptor<ApplicationStatusChangedEvent> statusEventCaptor;

    @Captor
    private ArgumentCaptor<ApplicationWithdrawnEvent> withdrawnEventCaptor;

    private static final String USERNAME = "john.doe";
    private static final String JOB_ID = UUID.randomUUID().toString();
    private static final String CV_ID = UUID.randomUUID().toString();
    private static final String APP_ID = "app-123";

    private CreateApplicationRequest validRequest;
    private Application savedApplication;
    private ApplicationResponse applicationResponse;

    @BeforeEach
    void setUp() {
        validRequest = CreateApplicationRequest.builder()
                .jobId(JOB_ID)
                .cvId(CV_ID)
                .note("I'm very interested in this position")
                .build();

        savedApplication = Application.builder()
                .id(APP_ID)
                .username(USERNAME)
                .jobId(JOB_ID)
                .cvId(CV_ID)
                .status(ApplicationStatus.APPLIED)
                .note("I'm very interested in this position")
                .createdAt(Instant.now())
                .build();

        applicationResponse = ApplicationResponse.builder()
                .id(APP_ID)
                .username(USERNAME)
                .jobId(JOB_ID)
                .cvId(CV_ID)
                .status(ApplicationStatus.APPLIED)
                .note("I'm very interested in this position")
                .build();
    }

    @Nested
    @DisplayName("createApplication()")
    class CreateApplicationTests {

        @Test
        @DisplayName("Should create application successfully and publish Kafka event")
        void shouldCreateApplicationSuccessfully() {
            given(applicationRepository.existsByUsernameAndJobId(USERNAME, JOB_ID)).willReturn(false);
            given(applicationMapper.toEntity(validRequest)).willReturn(new Application());
            given(applicationRepository.save(any(Application.class))).willReturn(savedApplication);
            given(applicationMapper.toResponse(savedApplication)).willReturn(applicationResponse);

            ApplicationResponse result = applicationService.createApplication(validRequest, USERNAME);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(APP_ID);
            assertThat(result.getStatus()).isEqualTo(ApplicationStatus.APPLIED);

            then(applicationRepository).should().existsByUsernameAndJobId(USERNAME, JOB_ID);
            then(applicationRepository).should().save(any(Application.class));

            then(eventProducer).should().publishApplicationCreatedEvent(createdEventCaptor.capture());
            ApplicationCreatedEvent event = createdEventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo("APPLICATION_CREATED");
            assertThat(event.getData().getApplicationId()).isEqualTo(APP_ID);
            assertThat(event.getData().getUsername()).isEqualTo(USERNAME);
            assertThat(event.getData().getJobId()).isEqualTo(JOB_ID);
            assertThat(event.getData().getCvId()).isEqualTo(CV_ID);
        }

        @Test
        @DisplayName("Should throw ApplicationConflictException when user already applied")
        void shouldThrowConflictWhenAlreadyApplied() {
            given(applicationRepository.existsByUsernameAndJobId(USERNAME, JOB_ID)).willReturn(true);

            assertThatThrownBy(() -> applicationService.createApplication(validRequest, USERNAME))
                    .isInstanceOf(ApplicationConflictException.class)
                    .hasMessageContaining("already applied");

            then(applicationRepository).should(never()).save(any());
            then(eventProducer).should(never()).publishApplicationCreatedEvent(any());
        }
    }

    @Nested
    @DisplayName("getApplicationById()")
    class GetApplicationByIdTests {

        @Test
        @DisplayName("Should return application when found")
        void shouldReturnApplicationWhenFound() {
            given(applicationRepository.findById(APP_ID)).willReturn(Optional.of(savedApplication));
            given(applicationMapper.toResponse(savedApplication)).willReturn(applicationResponse);

            ApplicationResponse result = applicationService.getApplicationById(APP_ID);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(APP_ID);
            assertThat(result.getUsername()).isEqualTo(USERNAME);
        }

        @Test
        @DisplayName("Should throw NotFoundException when application doesn't exist")
        void shouldThrowNotFoundWhenMissing() {
            given(applicationRepository.findById(APP_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> applicationService.getApplicationById(APP_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("getMyApplications()")
    class GetMyApplicationsTests {

        @Test
        @DisplayName("Should return paginated applications for user")
        void shouldReturnPaginatedApplications() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Application> page = new PageImpl<>(List.of(savedApplication), pageable, 1);

            given(applicationRepository.findByUsername(USERNAME, pageable)).willReturn(page);
            given(applicationMapper.toResponse(any(Application.class))).willReturn(applicationResponse);

            ResultPaginationDTO<ApplicationResponse> result = applicationService.getMyApplications(USERNAME, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getMeta().getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return empty page when user has no applications")
        void shouldReturnEmptyPageWhenNoApplications() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Application> emptyPage = Page.empty(pageable);

            given(applicationRepository.findByUsername(USERNAME, pageable)).willReturn(emptyPage);

            ResultPaginationDTO<ApplicationResponse> result = applicationService.getMyApplications(USERNAME, pageable);

            assertThat(result.getItems()).isEmpty();
            assertThat(result.getMeta().getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatusTests {

        @Test
        @DisplayName("Should update status and publish Kafka event")
        void shouldUpdateStatusSuccessfully() {
            String updatedBy = "hr.manager";
            ApplicationStatus newStatus = ApplicationStatus.REVIEWING;

            given(applicationRepository.findById(APP_ID)).willReturn(Optional.of(savedApplication));
            given(applicationRepository.save(any(Application.class))).willReturn(savedApplication);
            given(applicationMapper.toResponse(any())).willReturn(applicationResponse);

            ApplicationResponse result = applicationService.updateStatus(APP_ID, newStatus, updatedBy);

            assertThat(result).isNotNull();
            then(applicationRepository).should().save(any(Application.class));

            then(eventProducer).should().publishStatusChangedEvent(statusEventCaptor.capture());
            ApplicationStatusChangedEvent event = statusEventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo("STATUS_CHANGED");
            assertThat(event.getData().getApplicationId()).isEqualTo(APP_ID);
            assertThat(event.getData().getNewStatus()).isEqualTo(newStatus);
            assertThat(event.getData().getChangedBy()).isEqualTo(updatedBy);
        }

        @Test
        @DisplayName("Should throw NotFoundException when application doesn't exist")
        void shouldThrowNotFoundWhenUpdatingNonexistent() {
            given(applicationRepository.findById(APP_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> applicationService.updateStatus(APP_ID, ApplicationStatus.REVIEWING, "hr"))
                    .isInstanceOf(NotFoundException.class);

            then(eventProducer).should(never()).publishStatusChangedEvent(any());
        }
    }

    @Nested
    @DisplayName("withdrawApplication()")
    class WithdrawApplicationTests {

        @Test
        @DisplayName("Should withdraw application and publish Kafka event when user is owner")
        void shouldWithdrawWhenOwner() {
            given(applicationRepository.findById(APP_ID)).willReturn(Optional.of(savedApplication));

            applicationService.withdrawApplication(APP_ID, USERNAME);

            then(applicationRepository).should().delete(savedApplication);

            then(eventProducer).should().publishApplicationWithdrawnEvent(withdrawnEventCaptor.capture());
            ApplicationWithdrawnEvent event = withdrawnEventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo("APPLICATION_WITHDRAWN");
            assertThat(event.getData().getApplicationId()).isEqualTo(APP_ID);
            assertThat(event.getData().getUsername()).isEqualTo(USERNAME);
            assertThat(event.getData().getJobId()).isEqualTo(JOB_ID);
        }

        @Test
        @DisplayName("Should throw ForbiddenException when user is not owner")
        void shouldThrowForbiddenWhenNotOwner() {
            given(applicationRepository.findById(APP_ID)).willReturn(Optional.of(savedApplication));

            assertThatThrownBy(() -> applicationService.withdrawApplication(APP_ID, "other-user"))
                    .isInstanceOf(ForbiddenException.class);

            then(applicationRepository).should(never()).delete(any());
            then(eventProducer).should(never()).publishApplicationWithdrawnEvent(any());
        }

        @Test
        @DisplayName("Should throw NotFoundException when application doesn't exist")
        void shouldThrowNotFoundWhenWithdrawingNonexistent() {
            given(applicationRepository.findById(APP_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> applicationService.withdrawApplication(APP_ID, USERNAME))
                    .isInstanceOf(NotFoundException.class);

            then(eventProducer).should(never()).publishApplicationWithdrawnEvent(any());
        }
    }

    @Nested
    @DisplayName("Statistics Methods")
    class StatisticsTests {

        @Test
        @DisplayName("Should return correct count by username")
        void shouldReturnCountByUsername() {
            given(applicationRepository.countByUsername(USERNAME)).willReturn(5L);

            long count = applicationService.countByUser(USERNAME);

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
            given(applicationRepository.existsByUsernameAndJobId(USERNAME, JOB_ID)).willReturn(true);

            boolean hasApplied = applicationService.hasUserAppliedToJob(USERNAME, JOB_ID);

            assertThat(hasApplied).isTrue();
        }

        @Test
        @DisplayName("Should return false when user hasn't applied to job")
        void shouldReturnFalseWhenNotApplied() {
            given(applicationRepository.existsByUsernameAndJobId(USERNAME, JOB_ID)).willReturn(false);

            boolean hasApplied = applicationService.hasUserAppliedToJob(USERNAME, JOB_ID);

            assertThat(hasApplied).isFalse();
        }
    }
}
