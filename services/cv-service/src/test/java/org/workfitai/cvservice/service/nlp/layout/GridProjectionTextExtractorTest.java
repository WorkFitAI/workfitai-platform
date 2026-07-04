package org.workfitai.cvservice.service.nlp.layout;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end validation of the grid-projection pipeline against the real,
 * PDFBox-rendered Phase 02 fixtures — proves the unit-tested pieces
 * (LineGrouper/AnchorDetector/GridColumnAssigner/ReadingOrderSorter/PlainJoiner)
 * actually compose correctly through {@link GridProjectionTextExtractor}, not
 * just in isolation.
 */
class GridProjectionTextExtractorTest {

    private static final Path FIXTURES_ROOT = Path.of("src/test/resources/cv-fixtures");

    private List<String> extractLines(String category) throws Exception {
        Path pdfPath = FIXTURES_ROOT.resolve(category).resolve("cv.pdf");
        try (InputStream in = Files.newInputStream(pdfPath);
                PDDocument doc = PDDocument.load(in)) {
            String text = new GridProjectionTextExtractor().extract(doc);
            return Arrays.stream(text.split("\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    @Test
    void singleColumn_preservesTopToBottomOrder_noRegression() throws Exception {
        List<String> lines = extractLines("single-column");

        assertThat(lines).containsExactly(
                "John Doe",
                "john.doe@example.com",
                "Summary",
                "Backend developer with five years of experience building scalable systems.",
                "Experience",
                "Senior Backend Engineer",
                "Designed and implemented microservices for the payment platform.",
                "Education",
                "Bachelor of Science in Computer Science",
                "Skills",
                "Java, Spring Boot, PostgreSQL, Docker");
    }

    @Test
    void twoColumnSidebar_emitsWholeLeftColumnBeforeWholeRightColumn_noInterleaving() throws Exception {
        List<String> lines = extractLines("two-column-sidebar");

        // The bug this phase fixes: a naive Y-then-X sort would merge "John Doe"
        // and "Experience" (same row, different columns) into one interleaved
        // line, burying "Experience" where parseCvText() could never recognize
        // it as a section header. Grid projection must keep them on separate
        // lines, with the whole left (sidebar) column emitted before the whole
        // right (main-body) column.
        assertThat(lines).noneMatch(line -> line.contains("John Doe") && line.contains("Experience"));
        assertThat(lines).contains("John Doe", "Skills", "Java", "React", "PostgreSQL",
                "Experience", "Senior Backend Engineer", "Education", "Bachelor of Science in Computer Science");

        int skillsIdx = lines.indexOf("Skills");
        int postgresIdx = lines.indexOf("PostgreSQL");
        int experienceIdx = lines.indexOf("Experience");
        int educationIdx = lines.indexOf("Education");
        // Whole left column (Skills..PostgreSQL) before whole right column (Experience..Education).
        assertThat(skillsIdx).isLessThan(postgresIdx);
        assertThat(postgresIdx).isLessThan(experienceIdx);
        assertThat(experienceIdx).isLessThan(educationIdx);
    }

    @Test
    void tableSkills_splitsGridCellsIntoSeparateEntries_noMerging() throws Exception {
        List<String> lines = extractLines("table-skills");

        // The bug this phase fixes: a naive sort merges each grid row into one
        // blob ("Java React Docker"); each cell must come out as its own entry.
        assertThat(lines).contains("Java", "React", "Docker", "PostgreSQL", "Kafka", "Redis",
                "MongoDB", "TypeScript", "GraphQL");
        assertThat(lines).noneMatch(line -> line.contains("Java") && line.contains("React"));
    }

    @Test
    void iconBulletHeaders_singleColumn_headersStayOnOwnLines() throws Exception {
        List<String> lines = extractLines("icon-bullet-headers");

        assertThat(lines).contains("John Doe", "*** Academic Journey ***",
                "Bachelor of Science in Computer Science", "### Languages I Speak ###",
                "English, Vietnamese", "Skills", "Java, Spring Boot");
    }

    /**
     * Regression for a real bug found in review: a job title (left margin) on
     * the same row as a right-aligned date range is an extremely common
     * single-column CV pattern. Dates of different string lengths don't share
     * one stable X anchor, so the date segments end up unanchored/"floating" —
     * without the ReadingOrderSorter row-preserving fix, ALL floating segments
     * were pooled to the very front of the whole document, so every date would
     * jump ahead of the person's name.
     */
    private static byte[] singleColumnWithInlineDates() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 750;
                y = drawLine(cs, 50, y, "John Doe");
                y = drawTitleAndDate(cs, y, "Experience", null);
                y = drawTitleAndDate(cs, y, "Senior Backend Engineer", "2020 - Present");
                y = drawLine(cs, 50, y, "Designed and implemented microservices for the payment platform.");
                y = drawTitleAndDate(cs, y, "Backend Engineer", "2018 - 2020");
                drawLine(cs, 50, y, "Built REST APIs serving over a million requests per day.");
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static float drawLine(PDPageContentStream cs, float x, float y, String text) throws Exception {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 11);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
        return y - 15;
    }

    private static float drawTitleAndDate(PDPageContentStream cs, float y, String title, String date)
            throws Exception {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 11);
        cs.newLineAtOffset(50, y);
        cs.showText(title);
        cs.endText();
        if (date != null) {
            float dateWidth = PDType1Font.HELVETICA.getStringWidth(date) / 1000 * 11;
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 11);
            cs.newLineAtOffset(560 - dateWidth, y);
            cs.showText(date);
            cs.endText();
        }
        return y - 15;
    }

    @Test
    void singleColumnWithInlineDates_datesStayWithTheirOwnRow_notScrambledToFront() throws Exception {
        byte[] pdfBytes = singleColumnWithInlineDates();
        List<String> lines;
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            String text = new GridProjectionTextExtractor().extract(doc);
            lines = Arrays.stream(text.split("\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }

        assertThat(lines.get(0)).isEqualTo("John Doe");
        assertThat(lines).contains("Experience", "Designed and implemented microservices for the payment platform.",
                "Built REST APIs serving over a million requests per day.");
        assertThat(lines).anyMatch(l -> l.contains("Senior Backend Engineer") && l.contains("2020 - Present"));
        assertThat(lines).anyMatch(l -> l.contains("Backend Engineer") && l.contains("2018 - 2020"));
        // Natural document order: name first, then experience entries top-to-bottom.
        assertThat(lines.indexOf("John Doe")).isLessThan(lines.indexOf("Experience"));
    }
}
