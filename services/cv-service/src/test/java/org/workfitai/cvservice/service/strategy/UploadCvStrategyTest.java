package org.workfitai.cvservice.service.strategy;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.model.dto.ParsedCvData;
import org.workfitai.cvservice.model.dto.request.ReqCvUploadDTO;
import org.workfitai.cvservice.model.enums.TemplateType;
import org.workfitai.cvservice.service.nlp.DynamicSkillExtractionService;
import org.workfitai.cvservice.service.nlp.PdfComplexityDetector;
import org.workfitai.cvservice.service.nlp.SemanticMatchingService;
import org.workfitai.cvservice.service.shared.FileService;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UploadCvStrategyTest {

    @Mock
    private SemanticMatchingService semanticMatchingService;

    @Mock
    private DynamicSkillExtractionService skillExtractor;

    @Mock
    private FileService fileService;

    private UploadCvStrategy strategy;

    private void init() {
        strategy = new UploadCvStrategy(semanticMatchingService, skillExtractor, fileService,
                new PdfComplexityDetector());
    }

    /** Generates a real minimal single-page PDF with the given lines (PDFBox can't parse fixtures it didn't write itself reliably without a real PDF structure). */
    private byte[] buildPdf(String... lines) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 700);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLineAtOffset(0, -15);
                }
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void createCv_uploadsFile_andParsesSectionsIntoCv() throws Exception {
        init();
        byte[] pdfBytes = buildPdf("Objective", "Looking for a challenging backend engineering role");
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        ReqCvUploadDTO dto = new ReqCvUploadDTO();
        dto.setFile(file);
        dto.setTemplateType(TemplateType.UPLOAD);

        when(fileService.uploadCV(file)).thenReturn("uuid-resume.pdf");
        when(fileService.generateFileUrl("uuid-resume.pdf")).thenReturn("http://minio/uuid-resume.pdf");
        when(semanticMatchingService.mapToCoreField("objective")).thenReturn("summary");
        when(skillExtractor.extractSkills(any())).thenReturn(List.of("Java", "Spring"));

        CV cv = strategy.createCv(dto);

        assertThat(dto.getObjectName()).isEqualTo("uuid-resume.pdf");
        assertThat(dto.getPdfUrl()).isEqualTo("http://minio/uuid-resume.pdf");
        @SuppressWarnings("unchecked")
        var skills = (java.util.List<String>) cv.getSections().get("skills");
        assertThat(skills).contains("Java", "Spring");
        assertThat(cv.getSummary()).contains("Looking for a challenging backend engineering role");
    }

    @Test
    void createCv_throwsRuntimeException_whenFileUploadFails() throws Exception {
        init();
        byte[] pdfBytes = buildPdf("Objective", "x");
        MockMultipartFile file = new MockMultipartFile("file", "x.pdf", "application/pdf", pdfBytes);
        ReqCvUploadDTO dto = new ReqCvUploadDTO();
        dto.setFile(file);
        when(fileService.uploadCV(file)).thenThrow(new RuntimeException("minio down"));

        assertThatThrownBy(() -> strategy.createCv(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Upload CV file failed");
    }

    @Test
    void createCv_throwsInvalidDataException_unwrapped_whenPdfHasNoExtractableText() throws Exception {
        // The OCR pre-check's InvalidDataException (a BusinessException) must survive
        // createCv()'s catch block as-is, not get flattened into a generic
        // RuntimeException("Upload CV file failed", ...) that would lose its 4xx status.
        init();
        byte[] pdfBytes = buildPdf(); // no text at all -> PdfComplexityDetector.Reason.NO_TEXT
        MockMultipartFile file = new MockMultipartFile("file", "scanned.pdf", "application/pdf", pdfBytes);
        ReqCvUploadDTO dto = new ReqCvUploadDTO();
        dto.setFile(file);
        when(fileService.uploadCV(file)).thenReturn("uuid-scanned.pdf");
        when(fileService.generateFileUrl("uuid-scanned.pdf")).thenReturn("http://minio/uuid-scanned.pdf");

        assertThatThrownBy(() -> strategy.createCv(dto))
                .isInstanceOf(org.workfitai.cvservice.errors.InvalidDataException.class);
    }

    @Test
    void parsePdfFile_extractsMultipleSectionsDirectly() throws Exception {
        init();
        byte[] pdfBytes = buildPdf(
                "Objective", "Backend developer with five years experience",
                "Education", "Bachelor of Science in Computer Science",
                "Experience", "Built scalable distributed systems for clients");
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        when(semanticMatchingService.mapToCoreField("objective")).thenReturn("summary");
        when(semanticMatchingService.mapToCoreField("education")).thenReturn("education");
        when(semanticMatchingService.mapToCoreField("experience")).thenReturn("experience");

        ParsedCvData parsed = strategy.parsePdfFile(file);

        // "Objective" maps to the dedicated "objective" bucket (not "summary") —
        // ParsedCvData keeps them separate; only CV.summary (buildSummaryText) merges them.
        assertThat(parsed.getObjective()).contains("Backend developer with five years experience");
        assertThat(parsed.getEducation()).contains("Bachelor of Science in Computer Science");
        assertThat(parsed.getExperience()).contains("Built scalable distributed systems for clients");
    }

    @Test
    void parsePdfFile_returnsEmptySections_whenNoHeadingsPresent() throws Exception {
        init();
        byte[] pdfBytes = buildPdf("Just some random text with no section headings at all");
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);

        ParsedCvData parsed = strategy.parsePdfFile(file);

        assertThat(parsed.getSummary()).isEmpty();
        assertThat(parsed.getSkills()).isEmpty();
    }

    @Test
    void parsePdfFile_filtersContactInfoLines() throws Exception {
        init();
        byte[] pdfBytes = buildPdf(
                "Skills",
                "john.doe@example.com",              // email — must be filtered
                "https://github.com/johndoe",         // URL — must be filtered
                "+84 098 765 4321",                   // phone number (>=8 digits) — must be filtered
                "Java frameworks and backend development" // 5 words — kept as content
        );
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        when(semanticMatchingService.mapToCoreField("skills")).thenReturn("skills");
        // 5-word content line also hits the header-detection branch (≤5 words) —
        // must explicitly resolve to "ignored" so it's kept as section content.
        when(semanticMatchingService.mapToCoreField("java frameworks and backend development")).thenReturn("ignored");
        when(skillExtractor.extractSkills(any())).thenReturn(List.of());

        ParsedCvData parsed = strategy.parsePdfFile(file);

        assertThat(parsed.getSkills())
                .containsExactly("Java frameworks and backend development");
        assertThat(parsed.getSkills())
                .doesNotContain("john.doe@example.com", "https://github.com/johndoe", "+84 098 765 4321");
    }

    @Test
    void parsePdfFile_shortLineStaysInCurrentSection_whenMappedToIgnored() throws Exception {
        init();
        // "Senior Developer" is 2 words (≤4) → mapToCoreField called → returns "ignored"
        // The code must NOT switch section; it should add the line as experience content.
        byte[] pdfBytes = buildPdf(
                "Experience",
                "Senior Developer",                              // short, maps to "ignored" → stays in experience
                "Built distributed systems for clients worldwide" // long content line
        );
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        when(semanticMatchingService.mapToCoreField("experience")).thenReturn("experience");
        when(semanticMatchingService.mapToCoreField("senior developer")).thenReturn("ignored");
        when(skillExtractor.extractSkills(any())).thenReturn(List.of());

        ParsedCvData parsed = strategy.parsePdfFile(file);

        assertThat(parsed.getExperience())
                .contains("Senior Developer", "Built distributed systems for clients worldwide");
    }

    @Test
    void parsePdfFile_minesSkillsFromExperienceText() throws Exception {
        init();
        byte[] pdfBytes = buildPdf(
                "Experience",
                "Developed REST APIs using Spring Boot and PostgreSQL database systems" // 10 words
        );
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        when(semanticMatchingService.mapToCoreField("experience")).thenReturn("experience");
        when(skillExtractor.extractSkills(any())).thenReturn(List.of("spring boot", "postgresql"));

        ParsedCvData parsed = strategy.parsePdfFile(file);

        // Skills section is empty, but mining from experience text populates it
        assertThat(parsed.getSkills()).contains("spring boot", "postgresql");
        assertThat(parsed.getExperience())
                .contains("Developed REST APIs using Spring Boot and PostgreSQL database systems");
    }

    @Test
    void parsePdfFile_deduplicatesSkillsBetweenSectionAndMined() throws Exception {
        init();
        // "Java" appears in the explicit skills section and is also returned by skillExtractor
        byte[] pdfBytes = buildPdf(
                "Skills",
                "Java",                                         // 1 word → maps to "ignored" → stays in skills
                "Experience",
                "Built microservices in Java using Spring Boot framework" // 8 words — mined
        );
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        when(semanticMatchingService.mapToCoreField("skills")).thenReturn("skills");
        when(semanticMatchingService.mapToCoreField("java")).thenReturn("ignored");
        when(semanticMatchingService.mapToCoreField("experience")).thenReturn("experience");
        when(skillExtractor.extractSkills(any())).thenReturn(List.of("Java", "spring boot"));

        ParsedCvData parsed = strategy.parsePdfFile(file);

        assertThat(parsed.getSkills()).contains("Java", "spring boot");
        // "Java" came from both section and mined — HashSet must deduplicate
        assertThat(parsed.getSkills().stream().filter("Java"::equals).count()).isEqualTo(1);
    }

    @Test
    void parsePdfFile_linesBeforeFirstSectionHeaderAreIgnored() throws Exception {
        init();
        // Before the first valid section header the parser sits in "ignored" —
        // no content should be collected regardless of line length.
        byte[] pdfBytes = buildPdf(
                "John Doe",                                      // 2 words → maps to "ignored" → not collected
                "Software Engineer",                             // 2 words → maps to "ignored" → not collected
                "Summary",                                       // section header
                "Experienced backend developer with five years of expertise" // collected
        );
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        when(semanticMatchingService.mapToCoreField("john doe")).thenReturn("ignored");
        when(semanticMatchingService.mapToCoreField("software engineer")).thenReturn("ignored");
        when(semanticMatchingService.mapToCoreField("summary")).thenReturn("summary");
        when(skillExtractor.extractSkills(any())).thenReturn(List.of());

        ParsedCvData parsed = strategy.parsePdfFile(file);

        assertThat(parsed.getSummary())
                .containsExactly("Experienced backend developer with five years of expertise");
        assertThat(parsed.getExperience()).isEmpty();
        assertThat(parsed.getSkills()).isEmpty();
    }

    @Test
    void parsePdfFile_filtersLinkedInAndWwwPrefixedContactLines() throws Exception {
        // Covers the remaining isContactInfo() OR branches not exercised by
        // parsePdfFile_filtersContactInfoLines (github.com / email / digits-only phone):
        // a bare "linkedin.com/..." line (no "http" prefix, no "@") and a bare
        // "www."-prefixed line (no "http" prefix either) must both still be filtered.
        init();
        byte[] pdfBytes = buildPdf(
                "Skills",
                "linkedin.com/in/johndoe",
                "www.johndoe.dev",
                "Java frameworks and backend development" // 5 words — kept as content
        );
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        when(semanticMatchingService.mapToCoreField("skills")).thenReturn("skills");
        when(semanticMatchingService.mapToCoreField("java frameworks and backend development")).thenReturn("ignored");
        when(skillExtractor.extractSkills(any())).thenReturn(List.of());

        ParsedCvData parsed = strategy.parsePdfFile(file);

        assertThat(parsed.getSkills())
                .containsExactly("Java frameworks and backend development");
        assertThat(parsed.getSkills())
                .doesNotContain("linkedin.com/in/johndoe", "www.johndoe.dev");
    }

    @Test
    void parsePdfFile_shortDigitLine_notMistakenForPhoneNumber() throws Exception {
        // isContactInfo()'s phone heuristic requires >=8 digits: a short numeric
        // token like a graduation year ("2024", 4 digits) must NOT be filtered as PII,
        // and — being <=5 words — falls through to header-detection, mapping to "ignored"
        // so it stays as regular section content. (Extra filler line keeps text density
        // above PdfComplexityDetector's sparse-text threshold.)
        init();
        byte[] pdfBytes = buildPdf(
                "Education",
                "Bachelor of Computer Science from a well known university",
                "2024");
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        when(semanticMatchingService.mapToCoreField("education")).thenReturn("education");
        when(semanticMatchingService.mapToCoreField("2024")).thenReturn("ignored");

        ParsedCvData parsed = strategy.parsePdfFile(file);

        assertThat(parsed.getEducation()).contains(
                "Bachelor of Computer Science from a well known university", "2024");
    }

    @Test
    void parsePdfFile_longSentenceWithManyDigits_notMistakenForPhoneNumber() throws Exception {
        // A long metric-bearing sentence can contain >=8 digits total, but isContactInfo()
        // also requires the whole line to be short (<=25 chars) — this line is long, so it
        // must be kept as real content, not dropped as PII.
        init();
        byte[] pdfBytes = buildPdf(
                "Experience",
                "Reduced infrastructure cost by 80000 dollars across 12 months in 2023");
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        when(semanticMatchingService.mapToCoreField("experience")).thenReturn("experience");

        ParsedCvData parsed = strategy.parsePdfFile(file);

        assertThat(parsed.getExperience())
                .containsExactly("Reduced infrastructure cost by 80000 dollars across 12 months in 2023");
    }

    @Test
    void createCv_buildsSummaryFromObjectiveOnly_whenNoDedicatedSummarySection() throws Exception {
        // buildSummaryText() null-checks summary and objective independently — a CV
        // with only an "Objective" section (no "Summary" section at all, so
        // ParsedCvData.summary stays an empty list, never null per parseCvText's
        // initialization) must still populate CV.summary from the objective lines.
        init();
        byte[] pdfBytes = buildPdf("Objective", "Seeking a challenging senior backend engineering role");
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        ReqCvUploadDTO dto = new ReqCvUploadDTO();
        dto.setFile(file);
        when(fileService.uploadCV(file)).thenReturn("uuid-resume.pdf");
        when(fileService.generateFileUrl("uuid-resume.pdf")).thenReturn("http://minio/uuid-resume.pdf");
        when(skillExtractor.extractSkills(any())).thenReturn(List.of());

        CV cv = strategy.createCv(dto);

        assertThat(cv.getSummary()).isEqualTo("Seeking a challenging senior backend engineering role");
    }

    @Test
    void parsePdfFile_unmappedSemanticSection_isTrackedButNeverAccumulatesContent() throws Exception {
        // Defensive branch: if the semantic matcher ever returns a section name that
        // isn't one of the known buckets (sectionData.containsKey(...) == false),
        // currentSection switches to it but subsequent lines are silently dropped
        // instead of throwing — parsing must not blow up on an unexpected mapping.
        init();
        byte[] pdfBytes = buildPdf(
                "References",
                "Available upon request from previous employers");
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        when(semanticMatchingService.mapToCoreField("references")).thenReturn("references");
        when(skillExtractor.extractSkills(any())).thenReturn(List.of());

        ParsedCvData parsed = strategy.parsePdfFile(file);

        assertThat(parsed.getExperience()).isEmpty();
        assertThat(parsed.getSkills()).isEmpty();
        assertThat(parsed.getSummary()).isEmpty();
    }

    @Test
    void parsePdfFile_jobTitleWithInlineDate_shortCircuitsHeaderDetection_staysAsContent() throws Exception {
        // Phase 05 verification: "Senior Backend Engineer 2020 - Present" cleans to
        // 5 words (<=5, would normally trigger header-detection) but contains
        // "engineer" (JOB_TITLE_WORDS) so looksLikeJobTitle() must short-circuit
        // it — mapToCoreField must NEVER be called for this line, and it must
        // stay as experience content, not get treated as a section header.
        init();
        byte[] pdfBytes = buildPdf(
                "Experience",
                "Senior Backend Engineer 2020 - Present",
                "Backend Engineer 2018 - 2020",
                "Built distributed systems for clients worldwide");
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);
        when(semanticMatchingService.mapToCoreField("experience")).thenReturn("experience");
        when(skillExtractor.extractSkills(any())).thenReturn(List.of());

        ParsedCvData parsed = strategy.parsePdfFile(file);

        // If looksLikeJobTitle() had failed to short-circuit, these lines would
        // either be misrouted to whatever section the unstubbed mock happens to
        // return, or (per Mockito's default) throw/return null and break parsing
        // — landing here unchanged, in the right section, is the proof.
        assertThat(parsed.getExperience()).containsExactly(
                "Senior Backend Engineer 2020 - Present",
                "Backend Engineer 2018 - 2020",
                "Built distributed systems for clients worldwide");
    }
}
