package org.workfitai.cvservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.workfitai.cvservice.client.JobServiceSkillClient;
import org.workfitai.cvservice.constant.CVConst;
import org.workfitai.cvservice.errors.InvalidDataException;
import org.workfitai.cvservice.messaging.CvEventProducer;
import org.workfitai.cvservice.messaging.NotificationProducer;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.ParsedCvData;
import org.workfitai.cvservice.model.dto.response.CvSnapshotResponse;
import org.workfitai.cvservice.repository.CVRepository;
import org.workfitai.cvservice.service.factory.CvCreationFactory;
import org.workfitai.cvservice.service.nlp.DynamicSkillExtractionService;
import org.workfitai.cvservice.service.nlp.PdfComplexityDetector;
import org.workfitai.cvservice.service.nlp.SemanticMatchingService;
import org.workfitai.cvservice.service.shared.FileService;
import org.workfitai.cvservice.service.strategy.UploadCvStrategy;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CVServiceApplicationSnapshotTest {

    private static final Path FIXTURES_ROOT = Path.of("src/test/resources/cv-fixtures");

    @Mock
    private CVRepository repository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private CvCreationFactory cvCreationFactory;
    @Mock
    private FileService fileService;
    @Mock
    private NotificationProducer notificationProducer;
    @Mock
    private CvEventProducer cvEventProducer;
    @Mock
    private UploadCvStrategy uploadCvStrategy;
    @Mock
    private WebClient webClient;
    @Mock
    private org.workfitai.cvservice.service.enrichment.CvSectionEnrichmentService cvSectionEnrichmentService;

    @InjectMocks
    private CVService cvService;

    @Test
    void createApplicationSnapshot_uploadsFileAndStoresObjectNameNamedAfterJob() throws Exception {
        MockMultipartFile file = new MockMultipartFile("cvPdfFile", "resume.pdf", "application/pdf", new byte[] { 1 });

        when(fileService.uploadCV(any(), anyString())).thenReturn("uuid-Senior_Backend_Developer.pdf");
        when(fileService.generateFileUrl("uuid-Senior_Backend_Developer.pdf"))
                .thenReturn("http://minio/cvs-files/uuid-Senior_Backend_Developer.pdf");
        when(uploadCvStrategy.parsePdfFileWithText(file)).thenReturn(new UploadCvStrategy.ParsedCvResult(
                "raw cv text",
                ParsedCvData.builder()
                        .headline("Backend Engineer")
                        .summary(java.util.List.of("Experienced engineer"))
                        .build()));
        when(repository.save(any(CV.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CvSnapshotResponse response = cvService.createApplicationSnapshot(
                "vanphat15it", "app-123", "job-1", "Senior Backend Developer", file);

        ArgumentCaptor<CV> savedCv = ArgumentCaptor.forClass(CV.class);
        verify(repository).save(savedCv.capture());

        assertThat(savedCv.getValue().getObjectName()).isEqualTo("uuid-Senior_Backend_Developer.pdf");
        assertThat(savedCv.getValue().getObjectName()).matches(CVConst.PDF_FILE_PATTERN);
        assertThat(savedCv.getValue().getPdfUrl()).isEqualTo("http://minio/cvs-files/uuid-Senior_Backend_Developer.pdf");
        assertThat(savedCv.getValue().getJobId()).isEqualTo("job-1");
        assertThat(savedCv.getValue().getApplicationId()).isEqualTo("app-123");
        assertThat(response.getCvId()).isNull(); // CV entity has no id assigned by the mock save
    }

    @Test
    void createApplicationSnapshot_passesJobNameToFileServiceForNaming() throws Exception {
        MockMultipartFile file = new MockMultipartFile("cvPdfFile", "resume.pdf", "application/pdf", new byte[] { 1 });

        when(fileService.uploadCV(any(), anyString())).thenReturn("uuid-Data_Scientist.pdf");
        when(fileService.generateFileUrl(anyString())).thenReturn("http://minio/cvs-files/uuid-Data_Scientist.pdf");
        when(uploadCvStrategy.parsePdfFileWithText(file)).thenReturn(new UploadCvStrategy.ParsedCvResult(
                "raw cv text", ParsedCvData.builder().summary(java.util.List.of()).build()));
        when(repository.save(any(CV.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cvService.createApplicationSnapshot("vanphat15it", "app-456", "job-2", "Data Scientist", file);

        verify(fileService).uploadCV(file, "Data Scientist");
    }

    // ---------------- Phase 06: real (non-mocked) pipeline coverage ----------------

    /** Builds a real UploadCvStrategy (real PDFBox extraction, real embedding model, real skill dictionary fallback), matching UploadCvStrategyGoldenFileTest's pattern. */
    private UploadCvStrategy buildRealUploadCvStrategy() {
        SemanticMatchingService semanticMatchingService = new SemanticMatchingService();
        semanticMatchingService.setThreshold(0.45);
        semanticMatchingService.setAnchors(Map.of(
                "summary", "professional summary, about me, executive profile, personal statement",
                "objective", "career objective, objective, professional goal, personal goal, career aim",
                "experience", "work experience, professional experience, employment history, work history",
                "skills", "technical skills, core competencies, technologies, programming languages, tools",
                "education",
                "academic background, academic journey, education, degrees, university, school, academic qualifications",
                "projects", "projects, personal projects, portfolio, side projects, open source work",
                "languages", "languages, language skills, spoken languages, foreign languages, language proficiency",
                "certifications",
                "certifications, certificates, licenses, professional certifications, awards, achievements"));
        semanticMatchingService.init();

        JobServiceSkillClient jobServiceSkillClient = mock(JobServiceSkillClient.class);
        when(jobServiceSkillClient.getAllSkills(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new RuntimeException("job-service not available in test — using fallback seed"));
        DynamicSkillExtractionService skillExtractor = new DynamicSkillExtractionService(jobServiceSkillClient);
        skillExtractor.refreshDictionary();

        return new UploadCvStrategy(semanticMatchingService, skillExtractor, mock(FileService.class),
                new PdfComplexityDetector());
    }

    /** CVService wired with a REAL UploadCvStrategy (everything else still mocked) — proves the real Phase 03/04/05 pipeline runs end-to-end through this call site, not just CVService's own orchestration. */
    private CVService buildCvServiceWithRealPipeline(FileService realPathFileService, WebClient realPathWebClient) {
        return new CVService(repository, mongoTemplate, cvCreationFactory, realPathFileService,
                notificationProducer, cvEventProducer, buildRealUploadCvStrategy(), realPathWebClient,
                cvSectionEnrichmentService);
    }

    private byte[] readFixture(String category) throws Exception {
        return Files.readAllBytes(FIXTURES_ROOT.resolve(category).resolve("cv.pdf"));
    }

    @Test
    void createApplicationSnapshot_realPipeline_parsesSingleColumnFixtureCorrectly() throws Exception {
        byte[] pdfBytes = readFixture("single-column");
        MockMultipartFile file = new MockMultipartFile("cvPdfFile", "resume.pdf", "application/pdf", pdfBytes);
        CVService realPipelineService = buildCvServiceWithRealPipeline(fileService, webClient);

        when(fileService.uploadCV(any(), anyString())).thenReturn("uuid-resume.pdf");
        when(fileService.generateFileUrl("uuid-resume.pdf")).thenReturn("http://minio/uuid-resume.pdf");
        when(repository.save(any(CV.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CvSnapshotResponse response = realPipelineService.createApplicationSnapshot(
                "vanphat15it", "app-real-1", "job-real-1", "Backend Engineer", file);

        assertThat(response.getExperience()).contains("Designed and implemented microservices");
        assertThat(response.getEducation()).contains("Bachelor of Science");
        assertThat(response.getSkills().toLowerCase()).contains("java");
    }

    @Test
    void createApplicationSnapshot_realPipeline_scannedPdf_throwsInvalidDataException_unwrapped() throws Exception {
        // Regression for the Phase 03 code-review finding: CVService's catch
        // block used to swallow InvalidDataException into a generic 500.
        byte[] pdfBytes = readFixture("scanned-image");
        MockMultipartFile file = new MockMultipartFile("cvPdfFile", "scanned.pdf", "application/pdf", pdfBytes);
        CVService realPipelineService = buildCvServiceWithRealPipeline(fileService, webClient);

        when(fileService.uploadCV(any(), anyString())).thenReturn("uuid-scanned.pdf");
        when(fileService.generateFileUrl("uuid-scanned.pdf")).thenReturn("http://minio/uuid-scanned.pdf");

        assertThatThrownBy(() -> realPipelineService.createApplicationSnapshot(
                "vanphat15it", "app-real-2", "job-real-2", "Backend Engineer", file))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void reparseApplicationSnapshot_realPipeline_reDownloadsAndParsesFixtureCorrectly() throws Exception {
        byte[] pdfBytes = readFixture("single-column");
        WebClient realPathWebClient = mock(WebClient.class, RETURNS_DEEP_STUBS);
        when(realPathWebClient.get().uri(any(URI.class)).retrieve().bodyToMono(byte[].class))
                .thenReturn(Mono.just(pdfBytes));
        CVService realPipelineService = buildCvServiceWithRealPipeline(fileService, realPathWebClient);

        when(fileService.uploadCvBytes(any(), org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString()))
                .thenReturn("uuid-reparsed.pdf");
        when(fileService.generateFileUrl("uuid-reparsed.pdf")).thenReturn("http://minio/uuid-reparsed.pdf");
        when(repository.save(any(CV.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CvSnapshotResponse response = realPipelineService.reparseApplicationSnapshot(
                "vanphat15it", "app-real-3", "Backend Engineer", "http://minio/original-uuid-resume.pdf");

        assertThat(response.getExperience()).contains("Designed and implemented microservices");
        assertThat(response.getEducation()).contains("Bachelor of Science");
        assertThat(response.getSkills().toLowerCase()).contains("java");
    }

    @Test
    void createApplicationSnapshot_wrapsGenericException_asRuntimeException() throws Exception {
        // Non-BusinessException failures (e.g. MinIO upload I/O errors) must be wrapped
        // into a generic RuntimeException instead of propagating raw, so callers get a
        // consistent 500-mapping contract.
        MockMultipartFile file = new MockMultipartFile("cvPdfFile", "resume.pdf", "application/pdf", new byte[] { 1 });
        when(fileService.uploadCV(any(), anyString())).thenThrow(new java.io.IOException("minio unreachable"));

        assertThatThrownBy(() -> cvService.createApplicationSnapshot(
                "vanphat15it", "app-789", "job-789", "Backend Engineer", file))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(InvalidDataException.class)
                .hasMessage("CV snapshot creation failed");
    }

    @Test
    void reparseApplicationSnapshot_throwsRuntimeException_whenDownloadedContentEmpty() {
        // The empty-content guard's RuntimeException is raised inside the same try
        // block as the rest of the reconciliation flow, so the outer catch(Exception)
        // re-wraps it into the generic "reparse failed" message — the original empty-
        // content detail survives only as the wrapped cause.
        WebClient realPathWebClient = mock(WebClient.class, RETURNS_DEEP_STUBS);
        when(realPathWebClient.get().uri(any(URI.class)).retrieve().bodyToMono(byte[].class))
                .thenReturn(Mono.just(new byte[0]));
        CVService realPipelineService = buildCvServiceWithRealPipeline(fileService, realPathWebClient);

        assertThatThrownBy(() -> realPipelineService.reparseApplicationSnapshot(
                "vanphat15it", "app-empty", "Backend Engineer", "http://minio/original-empty.pdf"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("CV snapshot reparse failed")
                .cause()
                .hasMessageContaining("Downloaded empty content");
    }

    @Test
    void reparseApplicationSnapshot_wrapsGenericException_asRuntimeException() throws Exception {
        // Non-BusinessException failures during reconciliation (e.g. MinIO re-upload
        // errors) must be wrapped, mirroring createApplicationSnapshot's contract.
        byte[] pdfBytes = readFixture("single-column");
        WebClient realPathWebClient = mock(WebClient.class, RETURNS_DEEP_STUBS);
        when(realPathWebClient.get().uri(any(URI.class)).retrieve().bodyToMono(byte[].class))
                .thenReturn(Mono.just(pdfBytes));
        CVService realPipelineService = buildCvServiceWithRealPipeline(fileService, realPathWebClient);

        when(fileService.uploadCvBytes(any(), org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString()))
                .thenThrow(new java.io.IOException("minio unreachable"));

        assertThatThrownBy(() -> realPipelineService.reparseApplicationSnapshot(
                "vanphat15it", "app-real-5", "Backend Engineer", "http://minio/original-uuid-resume.pdf"))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(InvalidDataException.class)
                .hasMessage("CV snapshot reparse failed");
    }

    @Test
    void reparseApplicationSnapshot_realPipeline_scannedPdf_throwsInvalidDataException_unwrapped() throws Exception {
        // Regression for the Phase 03 code-review finding, reparse-path side:
        // a re-downloaded scanned CV must surface InvalidDataException (400),
        // not a generic wrapped RuntimeException (500) — matters because
        // application-service's reconciliation job needs the distinct status to
        // know this is a permanent rejection, not a transient failure to retry.
        byte[] pdfBytes = readFixture("scanned-image");
        WebClient realPathWebClient = mock(WebClient.class, RETURNS_DEEP_STUBS);
        when(realPathWebClient.get().uri(any(URI.class)).retrieve().bodyToMono(byte[].class))
                .thenReturn(Mono.just(pdfBytes));
        CVService realPipelineService = buildCvServiceWithRealPipeline(fileService, realPathWebClient);

        when(fileService.uploadCvBytes(any(), org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString()))
                .thenReturn("uuid-scanned-reparse.pdf");
        when(fileService.generateFileUrl("uuid-scanned-reparse.pdf")).thenReturn("http://minio/uuid-scanned-reparse.pdf");

        assertThatThrownBy(() -> realPipelineService.reparseApplicationSnapshot(
                "vanphat15it", "app-real-4", "Backend Engineer", "http://minio/original-scanned.pdf"))
                .isInstanceOf(InvalidDataException.class);
    }
}
