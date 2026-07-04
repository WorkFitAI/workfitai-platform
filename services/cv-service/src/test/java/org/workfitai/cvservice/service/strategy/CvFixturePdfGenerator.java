package org.workfitai.cvservice.service.strategy;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test-only helper that generates synthetic, PDFBox-parseable CV PDFs for the
 * golden-file fixture set under {@code src/test/resources/cv-fixtures/}.
 * Not part of the default test suite (class name doesn't match Surefire's
 * default include patterns) — run manually via
 * {@code mvnw test -Dtest=CvFixturePdfGenerator} to (re)write fixtures to disk.
 */
class CvFixturePdfGenerator {

    private static final Path FIXTURES_ROOT = Path
            .of("src/test/resources/cv-fixtures");

    @Test
    void writeFixturesToDisk() throws Exception {
        write("single-column", singleColumn());
        write("two-column-sidebar", twoColumnSidebar());
        write("table-skills", tableSkills());
        write("icon-bullet-headers", iconBulletHeaders());
        write("scanned-image", scannedImage());
    }

    private static void write(String category, byte[] pdfBytes) throws IOException {
        Path dir = FIXTURES_ROOT.resolve(category);
        Files.createDirectories(dir);
        Files.write(dir.resolve("cv.pdf"), pdfBytes);
    }

    /** Draws a single line of text at an absolute (x, y) position, points from the page's bottom-left. */
    private static void drawTextAt(PDPageContentStream cs, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 11);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private static byte[] singleColumn() throws IOException {
        String[] lines = {
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
                "Java, Spring Boot, PostgreSQL, Docker"
        };
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 700;
                for (String line : lines) {
                    drawTextAt(cs, 50, y, line);
                    y -= 15;
                }
            }
            return toBytes(doc);
        }
    }

    /**
     * Left sidebar (name/contact/skills) and right body (experience/education)
     * share the same row y-coordinates so the current naive Y-then-X sort in
     * {@code UploadCvStrategy.extractPdfText()} interleaves them into merged,
     * garbled lines — this is the multi-column bug phase 04 fixes.
     */
    private static byte[] twoColumnSidebar() throws IOException {
        String[] left = {
                "John Doe",
                "john.doe@example.com",
                "Skills",
                "Java",
                "React",
                "PostgreSQL"
        };
        String[] right = {
                "Experience",
                "Senior Backend Engineer",
                "Designed and implemented microservices for payments.",
                "Led migration to microservices architecture.",
                "Education",
                "Bachelor of Science in Computer Science"
        };
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 700;
                for (int i = 0; i < left.length; i++) {
                    drawTextAt(cs, 50, y, left[i]);
                    drawTextAt(cs, 280, y, right[i]);
                    y -= 15;
                }
            }
            return toBytes(doc);
        }
    }

    /** A 3-column x 3-row skills grid under a "Skills" header — table-based layout. */
    private static byte[] tableSkills() throws IOException {
        String[][] grid = {
                { "Java", "React", "Docker" },
                { "PostgreSQL", "Kafka", "Redis" },
                { "MongoDB", "TypeScript", "GraphQL" }
        };
        float[] columnX = { 50, 200, 350 };
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawTextAt(cs, 50, 715, "Skills");
                float y = 700;
                for (String[] row : grid) {
                    for (int col = 0; col < row.length; col++) {
                        drawTextAt(cs, columnX[col], y, row[col]);
                    }
                    y -= 15;
                }
            }
            return toBytes(doc);
        }
    }

    /**
     * Section headers use icon/emoji-decorated synonyms instead of the exact
     * phrases in {@code SECTION_HEADER_MAP}, forcing the semantic-matching
     * fallback (real embedding model, no mocking) to classify them.
     */
    private static byte[] iconBulletHeaders() throws IOException {
        String[] lines = {
                "John Doe",
                "*** Academic Journey ***", // -> should map to "education"
                "Bachelor of Science in Computer Science",
                "### Languages I Speak ###", // -> should map to "languages"
                "English, Vietnamese",
                "Skills", // exact-match control line
                "Java, Spring Boot"
        };
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 700;
                for (String line : lines) {
                    drawTextAt(cs, 50, y, line);
                    y -= 15;
                }
            }
            return toBytes(doc);
        }
    }

    /** Image-only page (no text layer at all) — simulates a scanned/rasterized CV. */
    private static byte[] scannedImage() throws IOException {
        BufferedImage image = new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g.drawString("This is a scanned image with no extractable text layer.", 20, 100);
        g.dispose();

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, image);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(pdImage, 50, 550, image.getWidth(), image.getHeight());
            }
            return toBytes(doc);
        }
    }

    private static byte[] toBytes(PDDocument doc) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.save(baos);
        return baos.toByteArray();
    }
}
