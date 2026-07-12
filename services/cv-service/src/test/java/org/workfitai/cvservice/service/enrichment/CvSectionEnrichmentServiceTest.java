package org.workfitai.cvservice.service.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.cvservice.client.RecommendationEngineClient;
import org.workfitai.cvservice.client.dto.SectionExtractionResult;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.repository.CVRepository;

@ExtendWith(MockitoExtension.class)
class CvSectionEnrichmentServiceTest {

    @Mock
    private CVRepository repository;
    @Mock
    private RecommendationEngineClient recommendationEngineClient;

    @InjectMocks
    private CvSectionEnrichmentService service;

    @BeforeEach
    void enableFeature() {
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    private CV activeCv() {
        CV cv = new CV();
        cv.setCvId("cv-1");
        cv.setExist(true);
        cv.setSummary("old summary");
        cv.setSections(new HashMap<>(java.util.Map.of("experience", List.of("old exp"))));
        return cv;
    }

    private SectionExtractionResult extracted() {
        return SectionExtractionResult.builder()
                .extracted(true).summary("new sum").experience("new exp")
                .skills("new skills").education("new edu").build();
    }

    @Test
    void enrichAsync_overridesSectionsOnSuccess() {
        when(recommendationEngineClient.extractSections("raw")).thenReturn(extracted());
        when(repository.findById("cv-1")).thenReturn(Optional.of(activeCv()));

        service.enrichAsync("cv-1", "raw");

        ArgumentCaptor<CV> saved = ArgumentCaptor.forClass(CV.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSummary()).isEqualTo("new sum");
        assertThat(saved.getValue().getSections().get("experience")).isEqualTo(List.of("new exp"));
        assertThat(saved.getValue().getSections().get("skills")).isEqualTo(List.of("new skills"));
        assertThat(saved.getValue().getSections().get("education")).isEqualTo(List.of("new edu"));
        assertThat(saved.getValue().getOllamaExtractedAt()).isNotNull();
    }

    @Test
    void enrichAsync_notExtracted_marksAttemptedButKeepsHeuristicSections() {
        // extracted=false must still mark ollamaExtractedAt (an attempt WAS made,
        // Ollama just had nothing usable) — null only means "never even tried".
        when(recommendationEngineClient.extractSections("raw"))
                .thenReturn(SectionExtractionResult.notExtracted());
        when(repository.findById("cv-1")).thenReturn(Optional.of(activeCv()));

        service.enrichAsync("cv-1", "raw");

        ArgumentCaptor<CV> saved = ArgumentCaptor.forClass(CV.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getOllamaExtractedAt()).isNotNull();
        assertThat(saved.getValue().getSummary()).isEqualTo("old summary");
        assertThat(saved.getValue().getSections().get("experience")).isEqualTo(List.of("old exp"));
    }

    @Test
    void enrichAsync_blankText_isNoOp() {
        service.enrichAsync("cv-1", "   ");
        verify(recommendationEngineClient, never()).extractSections(anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void enrichAsync_disabled_isNoOp() {
        ReflectionTestUtils.setField(service, "enabled", false);
        service.enrichAsync("cv-1", "raw");
        verify(recommendationEngineClient, never()).extractSections(anyString());
    }

    @Test
    void enrichAsync_missingCv_doesNotSave() {
        when(recommendationEngineClient.extractSections("raw")).thenReturn(extracted());
        when(repository.findById("cv-1")).thenReturn(Optional.empty());

        service.enrichAsync("cv-1", "raw");

        verify(repository, never()).save(any());
    }

    @Test
    void enrichAsync_clientThrows_isSwallowed() {
        when(recommendationEngineClient.extractSections("raw")).thenThrow(new RuntimeException("boom"));
        // Should not propagate.
        service.enrichAsync("cv-1", "raw");
        verify(repository, never()).save(any());
    }

    @Test
    void enrichSnapshotAsync_overridesAndRepushesToCvReferStore() {
        when(recommendationEngineClient.extractSections("raw")).thenReturn(extracted());
        when(repository.findById("cv-1")).thenReturn(Optional.of(activeCv()));

        service.enrichSnapshotAsync("cv-1", "job-1", "alice", "raw");

        verify(repository).save(any(CV.class));
        verify(recommendationEngineClient).pushCvReferSnapshot(
                eq("job-1"), eq("alice"), eq("new sum"), eq("new exp"), eq("new skills"), eq("new edu"));
    }

    @Test
    void enrichAsync_blankOllamaField_keepsHeuristicSection() {
        // Ollama returns a strong experience but blank education → education must keep
        // its heuristic value (no data loss), experience must be overwritten.
        CV cv = activeCv();
        cv.getSections().put("education", List.of("BSc Heuristic"));
        SectionExtractionResult partial = SectionExtractionResult.builder()
                .extracted(true).summary("new sum").experience("new exp")
                .skills("new skills").education("").build();
        when(recommendationEngineClient.extractSections("raw")).thenReturn(partial);
        when(repository.findById("cv-1")).thenReturn(Optional.of(cv));

        service.enrichAsync("cv-1", "raw");

        ArgumentCaptor<CV> saved = ArgumentCaptor.forClass(CV.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSections().get("experience")).isEqualTo(List.of("new exp"));
        assertThat(saved.getValue().getSections().get("education")).isEqualTo(List.of("BSc Heuristic"));
    }

    @Test
    void enrichSnapshotAsync_pushesMergedValues_whenOllamaFieldBlank() {
        CV cv = activeCv();
        cv.getSections().put("education", List.of("BSc Heuristic"));
        SectionExtractionResult partial = SectionExtractionResult.builder()
                .extracted(true).summary("new sum").experience("new exp")
                .skills("new skills").education("").build();
        when(recommendationEngineClient.extractSections("raw")).thenReturn(partial);
        when(repository.findById("cv-1")).thenReturn(Optional.of(cv));

        service.enrichSnapshotAsync("cv-1", "job-1", "alice", "raw");

        verify(recommendationEngineClient).pushCvReferSnapshot(
                eq("job-1"), eq("alice"), eq("new sum"), eq("new exp"), eq("new skills"), eq("BSc Heuristic"));
    }

    @Test
    void enrichSnapshotAsync_notExtracted_marksAttempted_doesNotPush() {
        when(recommendationEngineClient.extractSections("raw"))
                .thenReturn(SectionExtractionResult.notExtracted());
        when(repository.findById("cv-1")).thenReturn(Optional.of(activeCv()));

        service.enrichSnapshotAsync("cv-1", "job-1", "alice", "raw");

        ArgumentCaptor<CV> saved = ArgumentCaptor.forClass(CV.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getOllamaExtractedAt()).isNotNull();
        verify(recommendationEngineClient, never()).pushCvReferSnapshot(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void enrichAsync_missingCv_doesNotMarkAttempted() {
        // CV not found (e.g. deleted mid-flight) — nothing to persist the marker on,
        // so it stays null and the next startup backfill will retry it.
        when(recommendationEngineClient.extractSections("raw")).thenReturn(extracted());
        when(repository.findById("cv-1")).thenReturn(Optional.empty());

        service.enrichAsync("cv-1", "raw");

        verify(repository, never()).save(any());
    }
}
