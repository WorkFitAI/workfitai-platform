package org.workfitai.cvservice.service.strategy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.workfitai.cvservice.client.JobServiceSkillClient;
import org.workfitai.cvservice.errors.InvalidDataException;
import org.workfitai.cvservice.model.dto.ParsedCvData;
import org.workfitai.cvservice.service.nlp.DynamicSkillExtractionService;
import org.workfitai.cvservice.service.nlp.PdfComplexityDetector;
import org.workfitai.cvservice.service.nlp.SemanticMatchingService;
import org.workfitai.cvservice.service.shared.FileService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 07: per-category CI regression gate over all {@code cv-fixtures/}
 * categories, replacing Phase 02's loose {@code UploadCvBaselineCaptureTest}
 * (which only spot-checked a few lines with {@code anyMatch}/{@code contains})
 * with a hard, data-driven comparison against each fixture's
 * {@code expected.json} — so a future change that silently regresses one
 * template category (e.g. two-column bleed coming back) fails a specific,
 * named test instead of being masked by an aggregate/spot-check.
 *
 * <p>Runs the REAL (non-mocked) pipeline: real PDFBox/grid-projection
 * extraction, real embedding model, real skill-dictionary fallback — matching
 * Phase 02's integration-style pattern, not {@code UploadCvStrategyParseSimulationTest}'s
 * mocked-model pattern.
 *
 * <p>Section comparison (summary/objective/experience/education/projects/
 * languages/certifications) is an order-insensitive EXACT set match against
 * {@code expected.sections} (parsers may legitimately reorder equivalent
 * content — see plan research report 2 §3).
 *
 * <p>"skills" is handled separately with two complementary, non-exact checks
 * rather than folded into the generic exact-match loop above, because
 * {@code ParsedCvData.getSkills()} is a single merged field — union of the
 * raw "Skills" header's own lines AND whatever
 * {@link DynamicSkillExtractionService} additionally mines from
 * experience/projects/summary/objective text — with no accessor exposing the
 * raw header bucket in isolation:
 * <ul>
 *   <li>{@link #assertSkillsBucketNotMerged} — the raw header bucket
 *       ({@code expected.sections.skills}) must be a SUBSET of the actual
 *       merged output (not exact equality), tolerating extra mined entries a
 *       future fixture might add. This is what actually catches a structural
 *       regression like grid-projection failing and collapsing several
 *       distinct skill lines into one merged/scrambled line — a pure
 *       word-presence check would NOT catch that, since the same skill
 *       *names* are all still textually present, just wrongly combined.</li>
 *   <li>{@link #assertSkillsRecall} — every skill name in the top-level
 *       {@code expected.skills} ground truth must be findable
 *       (word-boundary-safe, case-insensitive) somewhere in the merged
 *       output. This is what catches a fixture whose Skills header draws as
 *       one comma-joined PDF line (e.g. "Java, Spring Boot, PostgreSQL,
 *       Docker" — the pipeline never comma-splits within a line, so it stays
 *       ONE raw string in {@code getSkills()}), where the subset check above
 *       can only confirm the merged line is present, not that each
 *       individual name inside it is intact.</li>
 * </ul>
 */
class UploadCvStrategyGoldenFileTest {

    private static final Path FIXTURES_ROOT = Path.of("src/test/resources/cv-fixtures");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Fixture JSON also carries doc-only "_note"-style keys (e.g. "_orderingNote") — ignored, not part of the assertion contract. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FixtureExpectation(Map<String, List<String>> sections, List<String> skills, boolean isScanned) {
    }

    /** "skills" intentionally excluded — see class javadoc for why it can't be exact-matched here. */
    private static final Map<String, Function<ParsedCvData, List<String>>> SECTION_ACCESSORS = Map.of(
            "summary", ParsedCvData::getSummary,
            "objective", ParsedCvData::getObjective,
            "experience", ParsedCvData::getExperience,
            "education", ParsedCvData::getEducation,
            "projects", ParsedCvData::getProjects,
            "languages", ParsedCvData::getLanguages,
            "certifications", ParsedCvData::getCertifications);

    private UploadCvStrategy buildRealStrategy() {
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

        JobServiceSkillClient jobServiceSkillClient = Mockito.mock(JobServiceSkillClient.class);
        Mockito.when(jobServiceSkillClient.getAllSkills(Mockito.anyInt(), Mockito.anyInt()))
                .thenThrow(new RuntimeException("job-service not available in golden-file test — using fallback seed"));
        DynamicSkillExtractionService skillExtractor = new DynamicSkillExtractionService(jobServiceSkillClient);
        skillExtractor.refreshDictionary();

        FileService fileService = Mockito.mock(FileService.class);

        return new UploadCvStrategy(semanticMatchingService, skillExtractor, fileService,
                new PdfComplexityDetector());
    }

    private FixtureExpectation loadExpectation(String category) throws Exception {
        Path path = FIXTURES_ROOT.resolve(category).resolve("expected.json");
        return MAPPER.readValue(path.toFile(), FixtureExpectation.class);
    }

    private ParsedCvData parseFixture(UploadCvStrategy strategy, String category) throws Exception {
        Path pdfPath = FIXTURES_ROOT.resolve(category).resolve("cv.pdf");
        try (InputStream in = Files.newInputStream(pdfPath)) {
            return strategy.parsePdfFile(in);
        }
    }

    private void assertSectionsMatch(ParsedCvData parsed, Map<String, List<String>> expectedSections) {
        for (Map.Entry<String, Function<ParsedCvData, List<String>>> entry : SECTION_ACCESSORS.entrySet()) {
            String key = entry.getKey();
            List<String> actual = entry.getValue().apply(parsed);
            List<String> expected = expectedSections.getOrDefault(key, List.of());
            assertThat(new HashSet<>(actual))
                    .as("section '%s'", key)
                    .isEqualTo(new HashSet<>(expected));
        }
    }

    private void assertSkillsBucketNotMerged(ParsedCvData parsed, List<String> expectedRawBucketSkills) {
        // Subset (not exact equality): tolerates a future fixture where mining
        // adds extra entries beyond the raw "Skills" header bucket. What this
        // DOES still catch: the raw bucket's own lines getting merged/scrambled
        // together (e.g. grid-projection regressing back to naive space-joining,
        // which combines a table's separate skill cells into fewer, longer lines
        // that no longer equal any individual expected entry).
        assertThat(new HashSet<>(parsed.getSkills()))
                .as("raw skills bucket entries must survive intact (not merged) in %s", parsed.getSkills())
                .containsAll(new HashSet<>(expectedRawBucketSkills));
    }

    private void assertSkillsRecall(ParsedCvData parsed, List<String> expectedSkills) {
        String actualBlob = String.join(" | ", parsed.getSkills()).toLowerCase();
        for (String expectedSkill : expectedSkills) {
            // Word-boundary-safe (not a raw substring check): e.g. "go" must not
            // false-positive-match inside "django"/"mongo"/"google".
            Pattern wordBoundaryMatch = Pattern.compile(
                    "\\b" + Pattern.quote(expectedSkill.toLowerCase()) + "\\b");
            assertThat(wordBoundaryMatch.matcher(actualBlob).find())
                    .as("expected skill '%s' present in actual skills output %s", expectedSkill, parsed.getSkills())
                    .isTrue();
        }
    }

    private void assertFixtureMatchesGoldenFile(String category) throws Exception {
        FixtureExpectation expectation = loadExpectation(category);
        UploadCvStrategy strategy = buildRealStrategy();

        if (expectation.isScanned()) {
            assertThatThrownBy(() -> parseFixture(strategy, category))
                    .as("scanned fixture '%s' must be rejected, not silently parsed", category)
                    .isInstanceOf(InvalidDataException.class);
            return;
        }

        ParsedCvData parsed = parseFixture(strategy, category);
        assertSectionsMatch(parsed, expectation.sections());
        assertSkillsBucketNotMerged(parsed, expectation.sections().getOrDefault("skills", List.of()));
        assertSkillsRecall(parsed, expectation.skills());
    }

    @Test
    void singleColumn_matchesGoldenFile() throws Exception {
        assertFixtureMatchesGoldenFile("single-column");
    }

    @Test
    void twoColumnSidebar_matchesGoldenFile() throws Exception {
        assertFixtureMatchesGoldenFile("two-column-sidebar");
    }

    @Test
    void tableSkills_matchesGoldenFile() throws Exception {
        assertFixtureMatchesGoldenFile("table-skills");
    }

    @Test
    void iconBulletHeaders_matchesGoldenFile() throws Exception {
        assertFixtureMatchesGoldenFile("icon-bullet-headers");
    }

    @Test
    void scannedImage_matchesGoldenFile() throws Exception {
        assertFixtureMatchesGoldenFile("scanned-image");
    }
}
