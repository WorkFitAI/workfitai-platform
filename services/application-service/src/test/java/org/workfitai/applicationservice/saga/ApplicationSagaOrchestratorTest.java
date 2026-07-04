package org.workfitai.applicationservice.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import org.workfitai.applicationservice.dto.kafka.JobStatsUpdateEvent;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.workfitai.applicationservice.client.CvServiceClient;
import org.workfitai.applicationservice.client.UserServiceClient;
import org.workfitai.applicationservice.client.UserServiceClient.UserInfo;
import org.workfitai.applicationservice.dto.CvSnapshotResponse;
import org.workfitai.applicationservice.dto.FileUploadResult;
import org.workfitai.applicationservice.dto.JobInfo;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.port.outbound.EventPublisherPort;
import org.workfitai.applicationservice.port.outbound.FileStoragePort;
import org.workfitai.applicationservice.port.outbound.JobServicePort;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.validation.ValidationPipeline;

@ExtendWith(MockitoExtension.class)
class ApplicationSagaOrchestratorTest {

    @Mock ValidationPipeline validationPipeline;
    @Mock JobServicePort jobServicePort;
    @Mock FileStoragePort fileStoragePort;
    @Mock ApplicationRepository applicationRepository;
    @Mock EventPublisherPort eventPublisher;
    @Mock ApplicationMapper applicationMapper;
    @Mock UserServiceClient userServiceClient;
    @Mock CvServiceClient cvServiceClient;

    @InjectMocks ApplicationSagaOrchestrator orchestrator;

    private CreateApplicationRequest request;
    private JobInfo jobInfo;
    private FileUploadResult uploadResult;
    private CvSnapshotResponse snapshot;
    private Application savedApplication;
    private ApplicationResponse applicationResponse;
    private RestResponse<List<UserInfo>> usersResponse;

    @BeforeEach
    void setUp() {
        MockMultipartFile cv = new MockMultipartFile("cvPdfFile", "cv.pdf", "application/pdf", "pdf-bytes".getBytes());

        request = CreateApplicationRequest.builder()
                .jobId("00000000-0000-0000-0000-000000000001")
                .email("candidate@example.com")
                .cvPdfFile(cv)
                .coverLetter("I am excited")
                .build();

        jobInfo = JobInfo.builder()
                .postId("post-1")
                .title("Java Developer")
                .companyId("company-1")
                .companyName("Acme Corp")
                .createdBy("hr_user")
                .build();

        uploadResult = FileUploadResult.builder()
                .fileUrl("http://minio/bucket/cv.pdf")
                .fileName("cv.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .build();

        snapshot = CvSnapshotResponse.builder()
                .cvId("cv-snap-1")
                .summary("Experienced developer")
                .skills("Java, Spring")
                .build();

        savedApplication = Application.builder()
                .id("app-id-1")
                .username("candidate1")
                .jobId("00000000-0000-0000-0000-000000000001")
                .status(ApplicationStatus.APPLIED)
                .createdAt(Instant.now())
                .jobSnapshot(Application.JobSnapshot.builder()
                        .postId("post-1")
                        .title("Java Developer")
                        .companyName("Acme Corp")
                        .build())
                .build();

        applicationResponse = new ApplicationResponse();

        UserInfo candidateInfo = new UserInfo("uid1", "candidate1", "John Doe",
                "candidate@example.com", null, "CANDIDATE", "ACTIVE",
                null, null, null, null, null, null, null, null, null);
        UserInfo hrInfo = new UserInfo("uid2", "hr_user", "HR Person",
                "hr@example.com", null, "HR", "ACTIVE",
                "company-1", null, null, null, null, null, null, null, null);
        usersResponse = new RestResponse<>(200, "ok", List.of(candidateInfo, hrInfo));
    }

    // ─── Happy Path ───────────────────────────────────────────────────────────

    @Test
    void createApplication_happyPath_allSixStepsRunInOrder() {
        stubHappyPath();

        ApplicationResponse result = orchestrator.createApplication(request, "candidate1");

        assertThat(result).isEqualTo(applicationResponse);

        // Verify step ordering: fetch_job → upload → (snapshot optional) → save → publish
        InOrder order = inOrder(jobServicePort, fileStoragePort, applicationRepository, eventPublisher);
        order.verify(jobServicePort).validateAndGetJob("00000000-0000-0000-0000-000000000001");
        order.verify(fileStoragePort).uploadFile(any(), eq("candidate1"), anyString());
        order.verify(applicationRepository).save(any());
        order.verify(eventPublisher).publishApplicationCreated(any());
    }

    @Test
    void createApplication_happyPath_returnsResponseMappedFromSavedApplication() {
        stubHappyPath();

        ApplicationResponse result = orchestrator.createApplication(request, "candidate1");

        verify(applicationMapper).toResponse(savedApplication);
        assertThat(result).isEqualTo(applicationResponse);
    }

    // ─── Compensation: File Deleted Iff Uploaded ──────────────────────────────

    @Test
    void createApplication_saveFails_afterUpload_compensatesDeletesFile() {
        when(jobServicePort.validateAndGetJob(anyString())).thenReturn(jobInfo);
        when(fileStoragePort.uploadFile(any(), anyString(), anyString())).thenReturn(uploadResult);
        when(cvServiceClient.createApplicationSnapshot(anyString(), anyString(), anyString(), any()))
                .thenReturn(snapshot);
        when(applicationRepository.save(any())).thenThrow(new RuntimeException("MongoDB down"));

        assertThatThrownBy(() -> orchestrator.createApplication(request, "candidate1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("MongoDB down");

        // File MUST be deleted because upload succeeded before save failed
        verify(fileStoragePort).deleteFile(uploadResult.getFileUrl());
    }

    @Test
    void createApplication_validateFails_noUpload_noCompensation() {
        doThrow(new IllegalArgumentException("Duplicate application"))
                .when(validationPipeline).validate(any(), anyString());

        assertThatThrownBy(() -> orchestrator.createApplication(request, "candidate1"))
                .isInstanceOf(IllegalArgumentException.class);

        // Upload never happened — no file to compensate
        verify(fileStoragePort, never()).uploadFile(any(), anyString(), anyString());
        verify(fileStoragePort, never()).deleteFile(anyString());
    }

    @Test
    void createApplication_fetchJobFails_noUpload_noCompensation() {
        when(jobServicePort.validateAndGetJob(anyString())).thenThrow(new RuntimeException("job-service down"));

        assertThatThrownBy(() -> orchestrator.createApplication(request, "candidate1"))
                .isInstanceOf(RuntimeException.class);

        verify(fileStoragePort, never()).uploadFile(any(), anyString(), anyString());
        verify(fileStoragePort, never()).deleteFile(anyString());
    }

    // ─── Best-Effort Steps: SNAPSHOT_CV ───────────────────────────────────────

    @Test
    void createApplication_snapshotCvFails_sagaCompletesSuccessfully() {
        when(jobServicePort.validateAndGetJob(anyString())).thenReturn(jobInfo);
        when(fileStoragePort.uploadFile(any(), anyString(), anyString())).thenReturn(uploadResult);
        when(cvServiceClient.createApplicationSnapshot(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("cv-service timeout"));
        when(applicationRepository.save(any())).thenReturn(savedApplication);
        when(userServiceClient.getUsersByUsernames(anyList())).thenReturn(usersResponse);
        when(applicationMapper.toResponse(any())).thenReturn(applicationResponse);

        // Must NOT throw — snapshot is best-effort
        ApplicationResponse result = orchestrator.createApplication(request, "candidate1");

        assertThat(result).isNotNull();
        // File should NOT be deleted (save succeeded)
        verify(fileStoragePort, never()).deleteFile(anyString());
    }

    @Test
    void createApplication_snapshotCvFails_cvSnapshotIdIsNullInSavedEntity() {
        when(jobServicePort.validateAndGetJob(anyString())).thenReturn(jobInfo);
        when(fileStoragePort.uploadFile(any(), anyString(), anyString())).thenReturn(uploadResult);
        when(cvServiceClient.createApplicationSnapshot(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("cv-service down"));
        when(applicationRepository.save(any())).thenAnswer(inv -> {
            Application app = inv.getArgument(0);
            // When snapshot failed, cvSnapshotId must be null
            assertThat(app.getCvSnapshotId()).isNull();
            return savedApplication;
        });
        when(userServiceClient.getUsersByUsernames(anyList())).thenReturn(usersResponse);
        when(applicationMapper.toResponse(any())).thenReturn(applicationResponse);

        orchestrator.createApplication(request, "candidate1");
    }

    // ─── Best-Effort Steps: PUBLISH_EVENTS ────────────────────────────────────

    @Test
    void createApplication_publishEventsFails_sagaReturnsSuccessfully() {
        when(jobServicePort.validateAndGetJob(anyString())).thenReturn(jobInfo);
        when(fileStoragePort.uploadFile(any(), anyString(), anyString())).thenReturn(uploadResult);
        when(cvServiceClient.createApplicationSnapshot(anyString(), anyString(), anyString(), any()))
                .thenReturn(snapshot);
        when(applicationRepository.save(any())).thenReturn(savedApplication);
        doThrow(new RuntimeException("Kafka down")).when(eventPublisher).publishApplicationCreated(any());
        when(applicationMapper.toResponse(any())).thenReturn(applicationResponse);

        // Fire-and-forget — must NOT throw
        ApplicationResponse result = orchestrator.createApplication(request, "candidate1");

        assertThat(result).isNotNull();
        verify(fileStoragePort, never()).deleteFile(anyString());
    }

    // ─── validHR Branch ──────────────────────────────────────────────────────

    @Test
    void createApplication_createdByNull_assignedToIsNull() {
        JobInfo jobWithNullCreatedBy = JobInfo.builder()
                .postId("p1").title("Dev").companyId("c1").companyName("Co")
                .createdBy(null).build();
        stubWithJobInfo(jobWithNullCreatedBy);

        orchestrator.createApplication(request, "candidate1");

        verify(applicationRepository).save(argThat(app -> app.getAssignedTo() == null));
    }

    @Test
    void createApplication_createdByBlank_assignedToIsNull() {
        JobInfo jobWithBlankCreatedBy = JobInfo.builder()
                .postId("p1").title("Dev").companyId("c1").companyName("Co")
                .createdBy("   ").build();
        stubWithJobInfo(jobWithBlankCreatedBy);

        orchestrator.createApplication(request, "candidate1");

        verify(applicationRepository).save(argThat(app -> app.getAssignedTo() == null));
    }

    @Test
    void createApplication_createdBySystem_assignedToIsNull() {
        JobInfo jobWithSystem = JobInfo.builder()
                .postId("p1").title("Dev").companyId("c1").companyName("Co")
                .createdBy("system").build();
        stubWithJobInfo(jobWithSystem);

        orchestrator.createApplication(request, "candidate1");

        verify(applicationRepository).save(argThat(app -> app.getAssignedTo() == null));
    }

    @Test
    void createApplication_validHR_assignedToIsSetToHrUsername() {
        stubHappyPath();

        orchestrator.createApplication(request, "candidate1");

        // jobInfo.createdBy = "hr_user" (valid HR) → assignedTo = "hr_user"
        verify(applicationRepository).save(argThat(app -> "hr_user".equals(app.getAssignedTo())));
    }

    // ─── HR Notification Branch ───────────────────────────────────────────────

    @Test
    void createApplication_validHR_bothCandidateAndHrNotified() {
        stubHappyPath();

        orchestrator.createApplication(request, "candidate1");

        verify(eventPublisher).publishCandidateNotification(anyString(), anyString(), anyString(), anyString(), anyString(), any());
        verify(eventPublisher).publishHrNotification(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void createApplication_systemHR_onlyCandidateNotified() {
        JobInfo systemJob = JobInfo.builder()
                .postId("p1").title("Dev").companyId("c1").companyName("Co")
                .createdBy("system").build();

        when(jobServicePort.validateAndGetJob(anyString())).thenReturn(systemJob);
        when(fileStoragePort.uploadFile(any(), anyString(), anyString())).thenReturn(uploadResult);
        when(cvServiceClient.createApplicationSnapshot(anyString(), anyString(), anyString(), any()))
                .thenReturn(snapshot);
        when(applicationRepository.save(any())).thenReturn(savedApplication);

        UserInfo candidateInfo = new UserInfo("uid1", "candidate1", "John Doe",
                "candidate@example.com", null, "CANDIDATE", "ACTIVE",
                null, null, null, null, null, null, null, null, null);
        when(userServiceClient.getUsersByUsernames(anyList()))
                .thenReturn(new RestResponse<>(200, "ok", List.of(candidateInfo)));
        when(applicationMapper.toResponse(any())).thenReturn(applicationResponse);

        orchestrator.createApplication(request, "candidate1");

        verify(eventPublisher).publishCandidateNotification(anyString(), anyString(), anyString(), anyString(), anyString(), any());
        verify(eventPublisher, never()).publishHrNotification(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void createApplication_nullUsersResponse_noNotificationsCrash() {
        when(jobServicePort.validateAndGetJob(anyString())).thenReturn(jobInfo);
        when(fileStoragePort.uploadFile(any(), anyString(), anyString())).thenReturn(uploadResult);
        when(cvServiceClient.createApplicationSnapshot(anyString(), anyString(), anyString(), any()))
                .thenReturn(snapshot);
        when(applicationRepository.save(any())).thenReturn(savedApplication);
        when(userServiceClient.getUsersByUsernames(anyList())).thenReturn(null);
        when(applicationMapper.toResponse(any())).thenReturn(applicationResponse);

        // Must NOT throw — null response is handled gracefully (fire-and-forget)
        ApplicationResponse result = orchestrator.createApplication(request, "candidate1");

        assertThat(result).isNotNull();
    }

    // ─── CV Snapshot Linkage ──────────────────────────────────────────────────

    @Test
    void createApplication_snapshotAvailable_cvSnapshotIdSetOnEntity() {
        when(jobServicePort.validateAndGetJob(anyString())).thenReturn(jobInfo);
        when(fileStoragePort.uploadFile(any(), anyString(), anyString())).thenReturn(uploadResult);
        when(cvServiceClient.createApplicationSnapshot(anyString(), anyString(), anyString(), any()))
                .thenReturn(snapshot);
        when(applicationRepository.save(any())).thenAnswer(inv -> {
            Application app = inv.getArgument(0);
            assertThat(app.getCvSnapshotId()).isEqualTo("cv-snap-1");
            return savedApplication;
        });
        when(userServiceClient.getUsersByUsernames(anyList())).thenReturn(usersResponse);
        when(applicationMapper.toResponse(any())).thenReturn(applicationResponse);

        orchestrator.createApplication(request, "candidate1");
    }

    // ─── JOB_STATS_UPDATE ────────────────────────────────────────────────────

    @Test
    void createApplication_jobStatsUpdate_sendsIncrementOfOne() {
        stubHappyPath();

        orchestrator.createApplication(request, "candidate1");

        ArgumentCaptor<JobStatsUpdateEvent> captor = ArgumentCaptor.forClass(JobStatsUpdateEvent.class);
        verify(eventPublisher).publishJobStatsUpdate(captor.capture());

        JobStatsUpdateEvent statsEvent = captor.getValue();
        assertThat(statsEvent.getTotalApplications()).isEqualTo(1);
        assertThat(statsEvent.getOperation()).isEqualTo("INCREMENT");
        verify(applicationRepository, never()).countByJobIdAndDeletedAtIsNull(anyString());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void stubHappyPath() {
        when(jobServicePort.validateAndGetJob(anyString())).thenReturn(jobInfo);
        when(fileStoragePort.uploadFile(any(), anyString(), anyString())).thenReturn(uploadResult);
        when(cvServiceClient.createApplicationSnapshot(anyString(), anyString(), anyString(), any()))
                .thenReturn(snapshot);
        when(applicationRepository.save(any())).thenReturn(savedApplication);
        when(userServiceClient.getUsersByUsernames(anyList())).thenReturn(usersResponse);
        when(applicationMapper.toResponse(any())).thenReturn(applicationResponse);
    }

    private void stubWithJobInfo(JobInfo job) {
        when(jobServicePort.validateAndGetJob(anyString())).thenReturn(job);
        when(fileStoragePort.uploadFile(any(), anyString(), anyString())).thenReturn(uploadResult);
        when(cvServiceClient.createApplicationSnapshot(anyString(), anyString(), anyString(), any()))
                .thenReturn(snapshot);
        when(applicationRepository.save(any())).thenReturn(savedApplication);
        when(userServiceClient.getUsersByUsernames(anyList())).thenReturn(usersResponse);
        when(applicationMapper.toResponse(any())).thenReturn(applicationResponse);
    }

    /** Inline argThat lambda alias for readability. */
    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
