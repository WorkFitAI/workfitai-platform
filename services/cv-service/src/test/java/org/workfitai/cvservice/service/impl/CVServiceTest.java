package org.workfitai.cvservice.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.workfitai.cvservice.dto.kafka.CvUpdatedEvent;
import org.workfitai.cvservice.dto.kafka.NotificationEvent;
import org.workfitai.cvservice.errors.CVConflictException;
import org.workfitai.cvservice.errors.InvalidDataException;
import org.workfitai.cvservice.errors.ResourceNotFoundException;
import org.workfitai.cvservice.messaging.CvEventProducer;
import org.workfitai.cvservice.messaging.NotificationProducer;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.request.ReqCvDTO;
import org.workfitai.cvservice.model.dto.request.ReqCvTemplateDTO;
import org.workfitai.cvservice.model.dto.request.ReqCvUploadDTO;
import org.workfitai.cvservice.model.dto.response.CvDataResponse;
import org.workfitai.cvservice.model.dto.response.CvSnapshotResponse;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;
import org.workfitai.cvservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.cvservice.model.enums.TemplateType;
import org.workfitai.cvservice.repository.CVRepository;
import org.workfitai.cvservice.service.factory.CvCreationFactory;
import org.workfitai.cvservice.service.shared.FileService;
import org.workfitai.cvservice.service.strategy.CvCreationStrategy;
import org.workfitai.cvservice.service.strategy.UploadCvStrategy;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CVServiceTest {

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

    @InjectMocks
    private CVService cvService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthenticatedUser(String username) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("sub")).thenReturn(username);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ─── createCv ────────────────────────────────────────────────────────────────

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    void createCv_template_savesAndReturnsDto_andSendsNotification() {
        mockAuthenticatedUser("alice");
        CvCreationStrategy strategy = mock(CvCreationStrategy.class);
        when(cvCreationFactory.getStrategy("template")).thenReturn(strategy);
        CV builtCv = new CV();
        when(strategy.createCv(any())).thenReturn(builtCv);
        when(repository.save(any(CV.class))).thenAnswer(inv -> {
            CV arg = inv.getArgument(0);
            arg.setCvId("cv-1");
            return arg;
        });

        ResCvDTO result = cvService.createCv("template", new ReqCvTemplateDTO());

        assertThat(result.getCvId()).isEqualTo("cv-1");
        assertThat(builtCv.getBelongTo()).isEqualTo("alice");
        verify(notificationProducer).send(any(NotificationEvent.class));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    void createCv_upload_validatesFileFirst_thenDelegatesToStrategy() {
        mockAuthenticatedUser("bob");
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[] { 1 });
        ReqCvUploadDTO dto = new ReqCvUploadDTO();
        dto.setFile(file);
        dto.setTemplateType(TemplateType.UPLOAD);

        CvCreationStrategy strategy = mock(CvCreationStrategy.class);
        when(cvCreationFactory.getStrategy("upload")).thenReturn(strategy);
        CV builtCv = new CV();
        when(strategy.createCv(dto)).thenReturn(builtCv);
        when(repository.save(any(CV.class))).thenAnswer(inv -> inv.getArgument(0));

        ResCvDTO result = cvService.createCv("upload", dto);

        assertThat(result).isNotNull();
        verify(strategy).createCv(dto);
    }

    @Test
    void createCv_upload_throwsInvalidDataException_whenFileWrongContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain", new byte[] { 1 });
        ReqCvUploadDTO dto = new ReqCvUploadDTO();
        dto.setFile(file);

        assertThatThrownBy(() -> cvService.createCv("upload", dto))
                .isInstanceOf(InvalidDataException.class);

        verifyNoFactoryInteraction();
    }

    private void verifyNoFactoryInteraction() {
        org.mockito.Mockito.verifyNoInteractions(cvCreationFactory);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    void createCv_sendCvUploadNotification_swallowsException_whenNotificationProducerThrows() {
        mockAuthenticatedUser("alice");
        CvCreationStrategy strategy = mock(CvCreationStrategy.class);
        when(cvCreationFactory.getStrategy("template")).thenReturn(strategy);
        when(strategy.createCv(any())).thenReturn(new CV());
        when(repository.save(any(CV.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("kafka down")).when(notificationProducer).send(any());

        ResCvDTO result = cvService.createCv("template", new ReqCvTemplateDTO());

        assertThat(result).isNotNull();
    }

    // ─── getById ─────────────────────────────────────────────────────────────────

    @Test
    void getById_returnsDto_whenExists() {
        CV cv = new CV();
        cv.setCvId("cv-1");
        cv.setExist(true);
        when(repository.findById("cv-1")).thenReturn(Optional.of(cv));

        ResCvDTO result = cvService.getById("cv-1");

        assertThat(result.getCvId()).isEqualTo("cv-1");
    }

    @Test
    void getById_throwsResourceNotFoundException_whenAbsent() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cvService.getById("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_throwsResourceNotFoundException_whenSoftDeleted() {
        CV cv = new CV();
        cv.setCvId("cv-1");
        cv.setExist(false);
        when(repository.findById("cv-1")).thenReturn(Optional.of(cv));

        assertThatThrownBy(() -> cvService.getById("cv-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── getCVByBelongTo / WithFilter ─────────────────────────────────────────────

    /**
     * KNOWN BUG (pre-existing, out of scope for this test phase): getCVByBelongTo() passes an
     * immutable Map.of() into getCVByBelongToWithFilter(), but CvQueryBuilder.applyTemplateType
     * unconditionally calls filters.remove(...), which throws on any immutable Map. This test
     * pins the current (broken) behavior rather than silently working around it — flagged in
     * the phase report for a follow-up fix (e.g. CvQueryBuilder should copy the map first).
     */
    @Test
    void getCVByBelongTo_throwsUnsupportedOperationException_dueToImmutableFiltersMapBug() {
        assertThatThrownBy(() -> cvService.getCVByBelongTo("alice", 0, 10))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getCVByBelongToWithFilter_appliesPaginationMeta() {
        CV cv1 = new CV();
        cv1.setCvId("cv-1");
        when(mongoTemplate.count(any(Query.class), eq(CV.class))).thenReturn(11L);
        when(mongoTemplate.find(any(Query.class), eq(CV.class))).thenReturn(List.of(cv1));

        Map<String, Object> mutableFilters = new java.util.HashMap<>();
        mutableFilters.put("templateType", "TECH");

        ResultPaginationDTO<ResCvDTO> result = cvService.getCVByBelongToWithFilter(
                "alice", mutableFilters, 1, 5);

        assertThat(result.getResult()).hasSize(1);
        assertThat(result.getMeta().getPage()).isEqualTo(2);
        assertThat(result.getMeta().getPageSize()).isEqualTo(5);
        assertThat(result.getMeta().getPages()).isEqualTo(3);
        assertThat(result.getMeta().getTotal()).isEqualTo(11L);
    }

    // ─── update ──────────────────────────────────────────────────────────────────

    private ReqCvDTO sampleUpdateRequest() {
        return ReqCvDTO.builder()
                .headline("Senior Engineer")
                .summary("Updated summary")
                .pdfUrl("https://example.com/cv.pdf")
                .templateType(TemplateType.TECH)
                .sections(Map.of("experience", List.of("Built things")))
                .build();
    }

    @Test
    void update_success_savesAndPublishesEvent() {
        CV existing = new CV();
        existing.setCvId("cv-1");
        existing.setBelongTo("alice");
        existing.setExist(true);
        when(repository.findById("cv-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(CV.class))).thenAnswer(inv -> inv.getArgument(0));

        ResCvDTO result = cvService.update("cv-1", sampleUpdateRequest());

        assertThat(result.getCvId()).isEqualTo("cv-1");
        assertThat(result.getHeadline()).isEqualTo("Senior Engineer");
        ArgumentCaptor<CvUpdatedEvent> captor = ArgumentCaptor.forClass(CvUpdatedEvent.class);
        verify(cvEventProducer).sendCvUpdated(captor.capture());
        assertThat(captor.getValue().getCvId()).isEqualTo("cv-1");
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
    }

    @Test
    void update_throwsInvalidDataException_whenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cvService.update("missing", sampleUpdateRequest()))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void update_throwsCVConflictException_whenSoftDeleted() {
        CV existing = new CV();
        existing.setCvId("cv-1");
        existing.setExist(false);
        when(repository.findById("cv-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> cvService.update("cv-1", sampleUpdateRequest()))
                .isInstanceOf(CVConflictException.class);
    }

    @Test
    void update_publishCvUpdatedEvent_swallowsException_whenProducerThrows() {
        CV existing = new CV();
        existing.setCvId("cv-1");
        existing.setBelongTo("alice");
        existing.setExist(true);
        when(repository.findById("cv-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(CV.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("kafka down")).when(cvEventProducer).sendCvUpdated(any());

        ResCvDTO result = cvService.update("cv-1", sampleUpdateRequest());

        assertThat(result).isNotNull();
    }

    // ─── delete ──────────────────────────────────────────────────────────────────

    @Test
    void delete_softDeletes_whenExistsAndActive() {
        CV existing = new CV();
        existing.setCvId("cv-1");
        existing.setExist(true);
        when(repository.findById("cv-1")).thenReturn(Optional.of(existing));

        cvService.delete("cv-1");

        ArgumentCaptor<CV> captor = ArgumentCaptor.forClass(CV.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isExist()).isFalse();
    }

    @Test
    void delete_throwsInvalidDataException_whenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cvService.delete("missing"))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void delete_throwsCVConflictException_whenAlreadyDeleted() {
        CV existing = new CV();
        existing.setCvId("cv-1");
        existing.setExist(false);
        when(repository.findById("cv-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> cvService.delete("cv-1"))
                .isInstanceOf(CVConflictException.class);
        verify(repository, never()).save(any());
    }

    // ─── downloadCV ──────────────────────────────────────────────────────────────

    @Test
    void downloadCV_returnsStream_whenObjectNameValid() throws Exception {
        InputStream stream = mock(InputStream.class);
        when(fileService.downloadCV("file-1.pdf")).thenReturn(stream);

        InputStream result = cvService.downloadCV("file-1.pdf");

        assertThat(result).isSameAs(stream);
    }

    @Test
    void downloadCV_throwsInvalidDataException_whenObjectNamePatternInvalid() {
        assertThatThrownBy(() -> cvService.downloadCV("../etc/passwd"))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void downloadCV_throwsInvalidDataException_whenFileServiceThrows() throws Exception {
        when(fileService.downloadCV("file-1.pdf")).thenThrow(new RuntimeException("minio down"));

        assertThatThrownBy(() -> cvService.downloadCV("file-1.pdf"))
                .isInstanceOf(InvalidDataException.class);
    }

    // ─── getCvDataBatch ──────────────────────────────────────────────────────────

    @Test
    void getCvDataBatch_returnsEmptyList_whenUsernamesNull() {
        assertThat(cvService.getCvDataBatch(null)).isEmpty();
    }

    @Test
    void getCvDataBatch_returnsEmptyList_whenUsernamesEmpty() {
        assertThat(cvService.getCvDataBatch(List.of())).isEmpty();
    }

    @Test
    void getCvDataBatch_keepsLatestCvPerUser() {
        CV older = new CV();
        older.setBelongTo("alice");
        older.setSummary("old");
        older.setUpdatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        CV newer = new CV();
        newer.setBelongTo("alice");
        newer.setSummary("new");
        newer.setUpdatedAt(Instant.parse("2024-02-01T00:00:00Z"));
        newer.setSections(Map.of("experience", List.of("Did things"), "skills", "Java"));
        when(repository.findByBelongToInAndIsExistTrue(List.of("alice")))
                .thenReturn(List.of(older, newer));

        List<CvDataResponse> result = cvService.getCvDataBatch(List.of("alice"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getResumeSummary()).isEqualTo("new");
        assertThat(result.get(0).getResumeExperience()).isEqualTo("Did things");
        assertThat(result.get(0).getResumeSkills()).isEqualTo("Java");
        assertThat(result.get(0).getResumeEducation()).isEmpty();
    }

    @Test
    void getCvDataBatch_fallsBackToCreatedAt_whenUpdatedAtNull() {
        CV cv = new CV();
        cv.setBelongTo("bob");
        cv.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        cv.setSummary(null);
        when(repository.findByBelongToInAndIsExistTrue(List.of("bob"))).thenReturn(List.of(cv));

        List<CvDataResponse> result = cvService.getCvDataBatch(List.of("bob"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getResumeSummary()).isEmpty();
    }

    // ─── getCvSnapshotsByApplicationIds ──────────────────────────────────────────

    @Test
    void getCvSnapshotsByApplicationIds_returnsEmptyList_whenInputNull() {
        assertThat(cvService.getCvSnapshotsByApplicationIds(null)).isEmpty();
    }

    @Test
    void getCvSnapshotsByApplicationIds_returnsEmptyList_whenInputEmpty() {
        assertThat(cvService.getCvSnapshotsByApplicationIds(List.of())).isEmpty();
    }

    @Test
    void getCvSnapshotsByApplicationIds_mapsFoundSnapshots() {
        CV snapshot = new CV();
        snapshot.setCvId("snap-1");
        snapshot.setSummary("snapshot summary");
        snapshot.setSections(Map.of("skills", List.of("Java", "Spring")));
        when(repository.findAllById(List.of("snap-1"))).thenReturn(List.of(snapshot));

        List<CvSnapshotResponse> result = cvService.getCvSnapshotsByApplicationIds(List.of("snap-1"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCvId()).isEqualTo("snap-1");
        assertThat(result.get(0).getSummary()).isEqualTo("snapshot summary");
        assertThat(result.get(0).getSkills()).isEqualTo("Java\nSpring");
    }
}
