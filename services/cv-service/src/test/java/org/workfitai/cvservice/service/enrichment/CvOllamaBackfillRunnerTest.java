package org.workfitai.cvservice.service.enrichment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.cvservice.client.RecommendationEngineClient;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.repository.CVRepository;
import org.workfitai.cvservice.service.shared.FileService;
import org.workfitai.cvservice.service.strategy.UploadCvStrategy;

@ExtendWith(MockitoExtension.class)
class CvOllamaBackfillRunnerTest {

    @Mock
    private CVRepository repository;
    @Mock
    private CvSectionEnrichmentService enrichmentService;
    @Mock
    private FileService fileService;
    @Mock
    private UploadCvStrategy uploadCvStrategy;
    @Mock
    private RecommendationEngineClient recommendationEngineClient;

    private CvOllamaBackfillRunner runner;

    @BeforeEach
    void setUp() {
        runner = new CvOllamaBackfillRunner(
                repository, enrichmentService, fileService, uploadCvStrategy, recommendationEngineClient);
        ReflectionTestUtils.setField(runner, "enabled", true);
        ReflectionTestUtils.setField(runner, "batchSize", 50);
        ReflectionTestUtils.setField(runner, "readyWaitSeconds", 1);
        ReflectionTestUtils.setField(runner, "readyPollIntervalMs", 5L);
        // Not stubbed in every test (e.g. the disabled-feature test never calls it).
        lenient().when(recommendationEngineClient.isReachable()).thenReturn(true);
    }

    private CV regularCv() {
        CV cv = new CV();
        cv.setCvId("cv-1");
        cv.setBelongTo("alice");
        cv.setObjectName("uuid-resume.pdf");
        return cv;
    }

    private CV snapshotCv() {
        CV cv = new CV();
        cv.setCvId("cv-2");
        cv.setBelongTo("bob");
        cv.setObjectName("uuid-snapshot.pdf");
        cv.setApplicationId("app-1");
        cv.setJobId("job-1");
        return cv;
    }

    @Test
    void disabled_skipsEntirely() {
        ReflectionTestUtils.setField(runner, "enabled", false);

        runner.backfillUnattemptedCvs();

        verify(repository, never()).findByOllamaExtractedAtIsNullAndIsExistTrueAndObjectNameIsNotNull(any());
    }

    @Test
    void noCandidates_isNoOp() {
        when(repository.findByOllamaExtractedAtIsNullAndIsExistTrueAndObjectNameIsNotNull(any(Pageable.class)))
                .thenReturn(List.of());

        runner.backfillUnattemptedCvs();

        verify(enrichmentService, never()).enrichAsync(anyString(), anyString());
    }

    @Test
    void regularCv_downloadsAndSubmitsEnrichAsync() throws Exception {
        CV cv = regularCv();
        when(repository.findByOllamaExtractedAtIsNullAndIsExistTrueAndObjectNameIsNotNull(any(Pageable.class)))
                .thenReturn(List.of(cv));
        InputStream pdfStream = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
        when(fileService.downloadCV("uuid-resume.pdf")).thenReturn(pdfStream);
        when(uploadCvStrategy.extractRawText(pdfStream)).thenReturn("raw text");

        runner.backfillUnattemptedCvs();

        verify(enrichmentService).enrichAsync("cv-1", "raw text");
        verify(enrichmentService, never()).enrichSnapshotAsync(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void snapshotCvWithJobId_submitsEnrichSnapshotAsync() throws Exception {
        CV cv = snapshotCv();
        when(repository.findByOllamaExtractedAtIsNullAndIsExistTrueAndObjectNameIsNotNull(any(Pageable.class)))
                .thenReturn(List.of(cv));
        InputStream pdfStream = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
        when(fileService.downloadCV("uuid-snapshot.pdf")).thenReturn(pdfStream);
        when(uploadCvStrategy.extractRawText(pdfStream)).thenReturn("raw text");

        runner.backfillUnattemptedCvs();

        verify(enrichmentService).enrichSnapshotAsync(eq("cv-2"), eq("job-1"), eq("bob"), eq("raw text"));
        verify(enrichmentService, never()).enrichAsync(anyString(), anyString());
    }

    @Test
    void snapshotCvWithoutJobId_fallsBackToEnrichAsync() throws Exception {
        // Snapshot created before jobId was persisted — no cross-service call available,
        // so it degrades to a Mongo-only enrichment (no CvReferStore push).
        CV cv = snapshotCv();
        cv.setJobId(null);
        when(repository.findByOllamaExtractedAtIsNullAndIsExistTrueAndObjectNameIsNotNull(any(Pageable.class)))
                .thenReturn(List.of(cv));
        InputStream pdfStream = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
        when(fileService.downloadCV("uuid-snapshot.pdf")).thenReturn(pdfStream);
        when(uploadCvStrategy.extractRawText(pdfStream)).thenReturn("raw text");

        runner.backfillUnattemptedCvs();

        verify(enrichmentService).enrichAsync("cv-2", "raw text");
        verify(enrichmentService, never()).enrichSnapshotAsync(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void templateCv_withNoObjectName_isSkipped() {
        // The repository query already excludes objectName==null CVs — this test covers
        // the runtime guard in submitOne as defense-in-depth (e.g. if a candidate's
        // objectName is cleared between the query and processing).
        CV cv = new CV();
        cv.setCvId("cv-3");
        cv.setObjectName(null);
        when(repository.findByOllamaExtractedAtIsNullAndIsExistTrueAndObjectNameIsNotNull(any(Pageable.class)))
                .thenReturn(List.of(cv));

        runner.backfillUnattemptedCvs();

        verify(enrichmentService, never()).enrichAsync(anyString(), anyString());
    }

    @Test
    void oneCvFailsDownload_othersStillProcessed() throws Exception {
        CV failing = regularCv();
        failing.setCvId("cv-fail");
        failing.setObjectName("missing.pdf");
        CV ok = regularCv();

        when(repository.findByOllamaExtractedAtIsNullAndIsExistTrueAndObjectNameIsNotNull(any(Pageable.class)))
                .thenReturn(List.of(failing, ok));
        when(fileService.downloadCV("missing.pdf")).thenThrow(new RuntimeException("object not found"));
        InputStream pdfStream = new ByteArrayInputStream(new byte[] { 1 });
        when(fileService.downloadCV("uuid-resume.pdf")).thenReturn(pdfStream);
        when(uploadCvStrategy.extractRawText(pdfStream)).thenReturn("raw text");

        runner.backfillUnattemptedCvs();

        verify(enrichmentService, never()).enrichAsync(eq("cv-fail"), anyString());
        verify(enrichmentService).enrichAsync("cv-1", "raw text");
    }

    @Test
    void recommendationEngineNeverReachable_skipsBatchWithoutQueryingCandidates() {
        // Regression: cv-service becomes ready well before recommendation-engine finishes
        // loading its ML models. Firing the one-shot batch anyway would burn every candidate's
        // only per-restart attempt on a connectivity failure it can never recover from this pass.
        when(recommendationEngineClient.isReachable()).thenReturn(false);

        runner.backfillUnattemptedCvs();

        verify(repository, never()).findByOllamaExtractedAtIsNullAndIsExistTrueAndObjectNameIsNotNull(any());
        verify(enrichmentService, never()).enrichAsync(anyString(), anyString());
    }

    @Test
    void recommendationEngineBecomesReachableDuringWait_thenSubmitsBatch() throws Exception {
        CV cv = regularCv();
        when(recommendationEngineClient.isReachable())
                .thenReturn(false, false, true); // not ready, not ready, then ready
        when(repository.findByOllamaExtractedAtIsNullAndIsExistTrueAndObjectNameIsNotNull(any(Pageable.class)))
                .thenReturn(List.of(cv));
        InputStream pdfStream = new ByteArrayInputStream(new byte[] { 1 });
        when(fileService.downloadCV("uuid-resume.pdf")).thenReturn(pdfStream);
        when(uploadCvStrategy.extractRawText(pdfStream)).thenReturn("raw text");

        runner.backfillUnattemptedCvs();

        verify(recommendationEngineClient, atLeast(3)).isReachable();
        verify(enrichmentService).enrichAsync("cv-1", "raw text");
    }
}
