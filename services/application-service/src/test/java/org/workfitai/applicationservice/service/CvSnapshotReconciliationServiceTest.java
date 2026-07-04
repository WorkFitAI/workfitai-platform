package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.applicationservice.client.CvServiceClient;
import org.workfitai.applicationservice.client.RecommendationEngineClient;
import org.workfitai.applicationservice.dto.CvSnapshotPushRequest;
import org.workfitai.applicationservice.dto.CvSnapshotResponse;
import org.workfitai.applicationservice.dto.ReparseSnapshotRequest;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
class CvSnapshotReconciliationServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock CvServiceClient cvServiceClient;
    @Mock RecommendationEngineClient recommendationEngineClient;

    @InjectMocks CvSnapshotReconciliationService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    @Test
    void reconcileMissingSnapshots_disabled_skipsRepositoryLookup() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.reconcileMissingSnapshots();

        verifyNoInteractions(applicationRepository, cvServiceClient, recommendationEngineClient);
    }

    @Test
    void reconcileMissingSnapshots_noCandidates_skipsClients() {
        when(applicationRepository.findByCvSnapshotIdIsNullAndStatusInAndDeletedAtIsNull(any()))
                .thenReturn(List.of());

        service.reconcileMissingSnapshots();

        verifyNoInteractions(cvServiceClient, recommendationEngineClient);
    }

    @Test
    void reconcileMissingSnapshots_success_savesSnapshotAndPushesToRecommendationEngine() {
        Application application = application("app-1", "candidate1", "job-1");
        when(applicationRepository.findByCvSnapshotIdIsNullAndStatusInAndDeletedAtIsNull(any()))
                .thenReturn(List.of(application));
        when(cvServiceClient.reparseSnapshot(any())).thenReturn(snapshot("cv-1"));

        service.reconcileMissingSnapshots();

        ArgumentCaptor<ReparseSnapshotRequest> reparseCaptor =
                ArgumentCaptor.forClass(ReparseSnapshotRequest.class);
        verify(cvServiceClient).reparseSnapshot(reparseCaptor.capture());
        assertThat(reparseCaptor.getValue().getUsername()).isEqualTo("candidate1");
        assertThat(reparseCaptor.getValue().getApplicationId()).isEqualTo("app-1");
        assertThat(reparseCaptor.getValue().getJobName()).isEqualTo("Backend Engineer");
        assertThat(reparseCaptor.getValue().getCvFileUrl()).isEqualTo("https://files/cv.pdf");

        assertThat(application.getCvSnapshotId()).isEqualTo("cv-1");
        verify(applicationRepository).save(application);

        ArgumentCaptor<CvSnapshotPushRequest> pushCaptor =
                ArgumentCaptor.forClass(CvSnapshotPushRequest.class);
        verify(recommendationEngineClient).pushCvSnapshot(pushCaptor.capture());
        assertThat(pushCaptor.getValue().getJobId()).isEqualTo("job-1");
        assertThat(pushCaptor.getValue().getUsername()).isEqualTo("candidate1");
        assertThat(pushCaptor.getValue().getSummary()).isEqualTo("summary-cv-1");
        assertThat(pushCaptor.getValue().getExperience()).isEqualTo("experience-cv-1");
        assertThat(pushCaptor.getValue().getSkills()).isEqualTo("skills-cv-1");
        assertThat(pushCaptor.getValue().getEducation()).isEqualTo("education-cv-1");
    }

    @Test
    void reconcileMissingSnapshots_missingJobSnapshot_usesCvFallbackJobName() {
        Application application = application("app-1", "candidate1", "job-1");
        application.setJobSnapshot(null);
        when(applicationRepository.findByCvSnapshotIdIsNullAndStatusInAndDeletedAtIsNull(any()))
                .thenReturn(List.of(application));
        when(cvServiceClient.reparseSnapshot(any())).thenReturn(snapshot("cv-1"));

        service.reconcileMissingSnapshots();

        ArgumentCaptor<ReparseSnapshotRequest> captor =
                ArgumentCaptor.forClass(ReparseSnapshotRequest.class);
        verify(cvServiceClient).reparseSnapshot(captor.capture());
        assertThat(captor.getValue().getJobName()).isEqualTo("cv");
    }

    @Test
    void reconcileMissingSnapshots_pushFails_keepsSavedSnapshot() {
        Application application = application("app-1", "candidate1", "job-1");
        when(applicationRepository.findByCvSnapshotIdIsNullAndStatusInAndDeletedAtIsNull(any()))
                .thenReturn(List.of(application));
        when(cvServiceClient.reparseSnapshot(any())).thenReturn(snapshot("cv-1"));
        doThrow(new RuntimeException("recommendation down"))
                .when(recommendationEngineClient).pushCvSnapshot(any());

        service.reconcileMissingSnapshots();

        assertThat(application.getCvSnapshotId()).isEqualTo("cv-1");
        verify(applicationRepository).save(application);
    }

    @Test
    void reconcileMissingSnapshots_reparseFails_doesNotSaveOrPush() {
        Application application = application("app-1", "candidate1", "job-1");
        when(applicationRepository.findByCvSnapshotIdIsNullAndStatusInAndDeletedAtIsNull(any()))
                .thenReturn(List.of(application));
        when(cvServiceClient.reparseSnapshot(any())).thenThrow(new RuntimeException("cv down"));

        service.reconcileMissingSnapshots();

        assertThat(application.getCvSnapshotId()).isNull();
        verify(applicationRepository, never()).save(any());
        verifyNoInteractions(recommendationEngineClient);
    }

    @Test
    void reconcileMissingSnapshots_largeBacklog_processesAtMostTwentyFive() {
        List<Application> applications = IntStream.range(0, 30)
                .mapToObj(i -> application("app-" + i, "candidate" + i, "job-" + i))
                .toList();
        when(applicationRepository.findByCvSnapshotIdIsNullAndStatusInAndDeletedAtIsNull(any()))
                .thenReturn(applications);
        when(cvServiceClient.reparseSnapshot(any())).thenAnswer(invocation -> {
            ReparseSnapshotRequest request = invocation.getArgument(0);
            return snapshot("cv-" + request.getApplicationId());
        });

        service.reconcileMissingSnapshots();

        verify(cvServiceClient, org.mockito.Mockito.times(25)).reparseSnapshot(any());
        verify(applicationRepository, org.mockito.Mockito.times(25)).save(any());
        assertThat(applications.subList(0, 25)).allMatch(app -> app.getCvSnapshotId() != null);
        assertThat(applications.subList(25, 30)).allMatch(app -> app.getCvSnapshotId() == null);
    }

    private Application application(String id, String username, String jobId) {
        return Application.builder()
                .id(id)
                .username(username)
                .jobId(jobId)
                .cvFileUrl("https://files/cv.pdf")
                .status(ApplicationStatus.APPLIED)
                .jobSnapshot(Application.JobSnapshot.builder()
                        .title("Backend Engineer")
                        .build())
                .build();
    }

    private CvSnapshotResponse snapshot(String cvId) {
        return CvSnapshotResponse.builder()
                .cvId(cvId)
                .summary("summary-" + cvId)
                .experience("experience-" + cvId)
                .skills("skills-" + cvId)
                .education("education-" + cvId)
                .build();
    }
}
