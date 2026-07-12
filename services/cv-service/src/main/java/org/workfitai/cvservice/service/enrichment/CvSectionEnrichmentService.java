package org.workfitai.cvservice.service.enrichment;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.workfitai.cvservice.client.RecommendationEngineClient;
import org.workfitai.cvservice.client.dto.SectionExtractionResult;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.repository.CVRepository;

/**
 * Async, best-effort re-extraction of the 4 resume sections (summary/experience/
 * skills/education) from raw CV text via Ollama (in recommendation-engine), run
 * AFTER the CV upload response is returned. On success it overrides the heuristic
 * PDFBox-parsed sections; on any failure the heuristic sections are left intact.
 *
 * Two entry points:
 *  - {@link #enrichAsync}         self-uploaded / created CVs (Mongo override only —
 *                                 they are not in any job's CvReferStore pool).
 *  - {@link #enrichSnapshotAsync} application-snapshot CVs (Mongo override PLUS a
 *                                 re-push into recommendation-engine's CvReferStore
 *                                 so HR ranking for the job reflects the new sections).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CvSectionEnrichmentService {

    private final CVRepository repository;
    private final RecommendationEngineClient recommendationEngineClient;

    @Value("${cv.ollama-extraction.enabled:true}")
    private boolean enabled;

    @Async("cvEnrichmentExecutor")
    public void enrichAsync(String cvId, String rawText) {
        if (!enabled || rawText == null || rawText.isBlank()) {
            return;
        }
        try {
            // Reaching this line means a real attempt is about to happen: the RE call is
            // made and awaited before touching Mongo, so ollamaExtractedAt only gets set
            // once we actually have a completed response (success, partial, or "nothing
            // usable" all count as "attempted" — see CV.ollamaExtractedAt javadoc).
            SectionExtractionResult result = recommendationEngineClient.extractSections(rawText);

            CV cv = loadActiveCv(cvId);
            if (cv == null) {
                return;
            }
            cv.setOllamaExtractedAt(Instant.now());

            if (result.isExtracted()) {
                applyOverride(cv, result);
                log.info("CV enrichment: overrode sections from Ollama for cvId={}", cvId);
            } else {
                log.debug("CV enrichment: Ollama attempted, no usable sections for cvId={} (heuristic kept)", cvId);
            }
            repository.save(cv);
        } catch (Exception e) {
            log.warn("CV enrichment failed for cvId={} (heuristic sections kept, not marked attempted — "
                    + "will retry on next startup backfill): {}", cvId, e.getMessage());
        }
    }

    @Async("cvEnrichmentExecutor")
    public void enrichSnapshotAsync(String cvId, String jobId, String username, String rawText) {
        if (!enabled || rawText == null || rawText.isBlank()) {
            return;
        }
        try {
            SectionExtractionResult result = recommendationEngineClient.extractSections(rawText);

            CV cv = loadActiveCv(cvId);
            if (cv == null) {
                return;
            }
            cv.setOllamaExtractedAt(Instant.now());

            if (!result.isExtracted()) {
                repository.save(cv);
                log.debug("CV snapshot enrichment: Ollama attempted, no usable sections for cvId={} (heuristic kept)", cvId);
                return;
            }

            MergedSections merged = applyOverride(cv, result);
            repository.save(cv);

            // Re-push the EFFECTIVE (post-merge) sections into CvReferStore so HR ranking
            // for this job uses the same values now stored in Mongo — Ollama fields where
            // present, heuristic fields where Ollama returned blank.
            recommendationEngineClient.pushCvReferSnapshot(
                    jobId, username,
                    merged.summary(), merged.experience(), merged.skills(), merged.education());
            log.info("CV snapshot enrichment: overrode sections + re-pushed for cvId={} jobId={} username={}",
                    cvId, jobId, username);
        } catch (Exception e) {
            log.warn("CV snapshot enrichment failed for cvId={} jobId={} (heuristic sections kept, not marked "
                    + "attempted — will retry on next startup backfill): {}", cvId, jobId, e.getMessage());
        }
    }

    private CV loadActiveCv(String cvId) {
        return repository.findById(cvId)
                .filter(CV::isExist)
                .orElse(null);
    }

    /**
     * Overwrite the 4 recommendation-relevant sections with the Ollama output, PER FIELD:
     * a field is only overwritten when Ollama returned a non-blank value for it. This avoids
     * wiping a good heuristic section when Ollama couldn't isolate that one section (it may
     * return "" for a section it genuinely couldn't extract while still returning others).
     *
     * {@code CV.summary} is a plain string; the sections map stores {@code List<String>}
     * (read back via {@code String.join("\n", list)}), so each overwritten value is stored
     * as a single-element list. Other section keys (projects, languages, certifications,
     * objective) are left untouched.
     *
     * @return the effective post-merge values (Ollama where present, heuristic otherwise) —
     *         used to keep the CvReferStore push consistent with what was written to Mongo.
     */
    private MergedSections applyOverride(CV cv, SectionExtractionResult result) {
        if (isNonBlank(result.getSummary())) {
            cv.setSummary(result.getSummary());
        }
        putIfPresent(cv, "experience", result.getExperience());
        putIfPresent(cv, "skills", result.getSkills());
        putIfPresent(cv, "education", result.getEducation());

        return new MergedSections(
                cv.getSummary() != null ? cv.getSummary() : "",
                currentSectionText(cv, "experience"),
                currentSectionText(cv, "skills"),
                currentSectionText(cv, "education"));
    }

    private void putIfPresent(CV cv, String key, String ollamaValue) {
        if (isNonBlank(ollamaValue) && cv.getSections() != null) {
            cv.getSections().put(key, List.of(ollamaValue));
        }
    }

    private static boolean isNonBlank(String s) {
        return s != null && !s.isBlank();
    }

    @SuppressWarnings("unchecked")
    private static String currentSectionText(CV cv, String key) {
        if (cv.getSections() == null) {
            return "";
        }
        Object value = cv.getSections().get(key);
        if (value instanceof List<?> list) {
            return String.join("\n", (List<String>) list);
        }
        return value != null ? value.toString() : "";
    }

    private record MergedSections(String summary, String experience, String skills, String education) {
    }
}
