package org.workfitai.cvservice.service.nlp;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class PdfComplexityDetectorTest {

    private final PdfComplexityDetector detector = new PdfComplexityDetector();

    private static PDPage blankPage(PDDocument doc) {
        PDPage page = new PDPage();
        doc.addPage(page);
        return page;
    }

    private static void drawText(PDDocument doc, PDPage page, String text) throws Exception {
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 12);
            cs.newLineAtOffset(50, 700);
            cs.showText(text);
            cs.endText();
        }
    }

    private static void drawImageOnly(PDDocument doc, PDPage page) throws Exception {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();
        PDImageXObject pdImage = LosslessFactory.createFromImage(doc, image);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.drawImage(pdImage, 50, 600, image.getWidth(), image.getHeight());
        }
    }

    @Test
    void emptyPage_isNoText() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            blankPage(doc);

            PdfComplexityDetector.Assessment result = detector.assess(doc);

            assertThat(result.needsOcr()).isTrue();
            assertThat(result.reason()).isEqualTo(PdfComplexityDetector.Reason.NO_TEXT);
        }
    }

    @Test
    void normalText_isOk() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = blankPage(doc);
            drawText(doc, page,
                    "Backend developer with five years of experience building scalable distributed systems.");

            PdfComplexityDetector.Assessment result = detector.assess(doc);

            assertThat(result.needsOcr()).isFalse();
            assertThat(result.reason()).isEqualTo(PdfComplexityDetector.Reason.NONE);
        }
    }

    @Test
    void imageOnlyMajorityOfPages_isImageOnly() throws Exception {
        // Page 1 has real text (keeps overall char count and density non-zero/non-sparse);
        // pages 2-3 are image-only with no text — 2/3 of pages => ratio 0.67 > 0.5 threshold.
        try (PDDocument doc = new PDDocument()) {
            PDPage textPage = blankPage(doc);
            drawText(doc, textPage,
                    "Backend developer with five years of experience building scalable distributed systems.");
            drawImageOnly(doc, blankPage(doc));
            drawImageOnly(doc, blankPage(doc));

            PdfComplexityDetector.Assessment result = detector.assess(doc);

            assertThat(result.needsOcr()).isTrue();
            assertThat(result.reason()).isEqualTo(PdfComplexityDetector.Reason.IMAGE_ONLY);
        }
    }

    @Test
    void imageOnlyExactlyHalfOfPages_isNotRejected() throws Exception {
        // A legitimate 2-page CV: page 1 real text, page 2 a scanned attachment
        // (certificate/ID) — exactly 50/50 split must NOT hard-reject the whole
        // upload just because one appended page is image-only.
        try (PDDocument doc = new PDDocument()) {
            PDPage textPage = blankPage(doc);
            drawText(doc, textPage,
                    "Backend developer with five years of experience building scalable distributed systems.");
            drawImageOnly(doc, blankPage(doc));

            PdfComplexityDetector.Assessment result = detector.assess(doc);

            assertThat(result.needsOcr()).isFalse();
            assertThat(result.reason()).isEqualTo(PdfComplexityDetector.Reason.NONE);
        }
    }

    @Test
    void sparseText_belowDensityThreshold_isSparseText() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = blankPage(doc);
            drawText(doc, page, "x");

            PdfComplexityDetector.Assessment result = detector.assess(doc);

            assertThat(result.needsOcr()).isTrue();
            assertThat(result.reason()).isEqualTo(PdfComplexityDetector.Reason.SPARSE_TEXT);
        }
    }

    @Test
    void garbledCharInjectedText_isGarbled() {
        String garbled = "�����abc";

        PdfComplexityDetector.Assessment result = detector.classifyTextDensity(
                garbled, 8, 484_704 /* Letter page area, pt^2 */);

        assertThat(result.needsOcr()).isTrue();
        assertThat(result.reason()).isEqualTo(PdfComplexityDetector.Reason.GARBLED);
    }

    @Test
    void vietnameseDiacritics_areNotFlaggedAsGarbled() {
        String vietnamese = "Kỹ sư phần mềm với nhiều năm kinh nghiệm xây dựng hệ thống phân tán.";

        PdfComplexityDetector.Assessment result = detector.classifyTextDensity(
                vietnamese, vietnamese.chars().filter(c -> !Character.isWhitespace(c)).count(), 484_704);

        assertThat(result.needsOcr()).isFalse();
        assertThat(result.reason()).isEqualTo(PdfComplexityDetector.Reason.NONE);
    }

    @Test
    void classifyTextDensity_zeroPageArea_fallsBackToMaxDensity_avoidingFalseSparseFlag() {
        // pageArea <= 0 is a defensive guard (e.g. a malformed/zero-size media box) —
        // density must fall back to Double.MAX_VALUE instead of dividing by zero, so a
        // tiny-but-real amount of text is never misclassified as SPARSE_TEXT here.
        String text = "Backend developer";

        PdfComplexityDetector.Assessment result = detector.classifyTextDensity(
                text, text.chars().filter(c -> !Character.isWhitespace(c)).count(), 0);

        assertThat(result.needsOcr()).isFalse();
        assertThat(result.reason()).isEqualTo(PdfComplexityDetector.Reason.NONE);
    }

    @Test
    void classifyTextDensity_newlineCarriageReturnAndTabControlChars_areNeverGarbled() {
        // \n, \r, \t are ISO control chars but are explicitly excluded from the garbled
        // count — normal line/paragraph formatting must never trigger a false GARBLED verdict.
        String text = "Backend developer\r\nwith\tfive years\r\nof experience building distributed systems for clients.";

        PdfComplexityDetector.Assessment result = detector.classifyTextDensity(
                text, text.chars().filter(c -> !Character.isWhitespace(c)).count(), 484_704);

        assertThat(result.needsOcr()).isFalse();
        assertThat(result.reason()).isEqualTo(PdfComplexityDetector.Reason.NONE);
    }

    @Test
    void classifyTextDensity_nonWhitespaceControlChars_areFlaggedAsGarbled() {
        // A genuine non-whitespace ISO control char (e.g. SOH/STX, from a PDF encoding/CID
        // failure) — unlike \n/\r/\t — must still count toward the garbled ratio.
        String garbled = "abc";

        PdfComplexityDetector.Assessment result = detector.classifyTextDensity(
                garbled, 8, 484_704);

        assertThat(result.needsOcr()).isTrue();
        assertThat(result.reason()).isEqualTo(PdfComplexityDetector.Reason.GARBLED);
    }
}
