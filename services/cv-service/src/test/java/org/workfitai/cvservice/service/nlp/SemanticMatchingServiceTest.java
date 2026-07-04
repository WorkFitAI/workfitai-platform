package org.workfitai.cvservice.service.nlp;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticMatchingServiceTest {

    private SemanticMatchingService buildService() {
        SemanticMatchingService service = new SemanticMatchingService();
        service.setThreshold(0.45);
        service.setAnchors(Map.of(
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
        service.init();
        return service;
    }

    /**
     * Phase 05 regression: Phase 02's icon-bullet-headers fixture uses
     * "*** Academic Journey ***" as a non-standard education header. Before
     * this fix, the real embedding model's highest-scoring anchor for
     * "academic journey" was actually "objective" (0.4054), not "education"
     * (0.3253) — both below the 0.45 threshold, so the header (and the
     * education content line after it) got silently dropped. Adding "academic
     * journey" to the education anchor phrase (application.yml) raises its
     * score to ~0.49, clearing the threshold and beating "objective".
     */
    @Test
    void academicJourneyHeader_mapsToEducation_notObjective() {
        SemanticMatchingService service = buildService();

        assertThat(service.mapToCoreField("academic journey")).isEqualTo("education");
    }

    @Test
    void academicJourneyPhrasing_containingTriggerPhrase_alsoMapsToEducation() {
        // Confirms the fix isn't a brittle exact-string match — a natural variant
        // containing the same trigger phrase also clears the threshold.
        SemanticMatchingService service = buildService();

        assertThat(service.mapToCoreField("my academic journey")).isEqualTo("education");
    }

    @Test
    void unrelatedEducationParaphrase_stillDoesNotClearThreshold() {
        // Honest boundary check, not a bug: "academic history" does NOT clear
        // the threshold with the current anchor phrase (measured, not assumed).
        // Per this phase's own rule ("do not change classification logic
        // speculatively — only retune based on measured evidence from Phase 02
        // fixtures"), broadening the anchor further isn't justified — no Phase
        // 02 fixture uses this phrasing. Documented here so a future paraphrase
        // failure isn't mistaken for new evidence of a regression.
        SemanticMatchingService service = buildService();

        assertThat(service.mapToCoreField("academic history")).isEqualTo("ignored");
    }

    @Test
    void languagesISpeakHeader_stillMapsToLanguages() {
        SemanticMatchingService service = buildService();

        assertThat(service.mapToCoreField("languages i speak")).isEqualTo("languages");
    }

    @Test
    void unrelatedShortPhrase_staysIgnored() {
        SemanticMatchingService service = buildService();

        assertThat(service.mapToCoreField("random unrelated words here")).isEqualTo("ignored");
    }
}
