package org.workfitai.cvservice.service.nlp;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Cheap PDFBox-only heuristic stack that flags scanned/no-text/sparse-text/
 * image-only/garbled PDFs BEFORE the section parser runs, so a scanned CV
 * surfaces a clear rejection instead of silently producing an empty/garbage
 * {@code ParsedCvData}. Not OCR itself — just a pre-check.
 */
@Service
public class PdfComplexityDetector {

    public enum Verdict {
        OK, NEEDS_OCR
    }

    public enum Reason {
        NONE, NO_TEXT, SPARSE_TEXT, IMAGE_ONLY, GARBLED
    }

    public record Assessment(Verdict verdict, Reason reason) {
        public static Assessment ok() {
            return new Assessment(Verdict.OK, Reason.NONE);
        }

        public static Assessment needsOcr(Reason reason) {
            return new Assessment(Verdict.NEEDS_OCR, reason);
        }

        public boolean needsOcr() {
            return verdict == Verdict.NEEDS_OCR;
        }
    }

    /**
     * Non-whitespace chars per page-area (pt²) below this is "sparse-text".
     * Calibrated well below the smallest existing unit-test fixture density
     * (~9.1e-5) so short-but-legitimate CVs never false-positive; only
     * near-empty documents trip this.
     */
    @Value("${cv-parser.ocr-detection.min-chars-per-page-area:0.00003}")
    private double minCharsPerPageArea = 0.00003;

    /** Replacement/control-char ratio above this is "garbled" (encoding/CID failure). */
    @Value("${cv-parser.ocr-detection.max-garbled-ratio:0.3}")
    private double maxGarbledRatio = 0.3;

    /**
     * Fraction of pages that are image-only (has an image XObject, ~no own text)
     * needed to flag "image-only". A strict majority (not "at least half") so a
     * legitimate CV with one scanned attachment page (e.g. a certificate/ID)
     * mixed in with real text pages isn't hard-rejected on a 50/50 split.
     */
    @Value("${cv-parser.ocr-detection.min-image-only-page-ratio:0.5}")
    private double minImageOnlyPageRatio = 0.5;

    public Assessment assess(PDDocument doc) throws IOException {
        String text = new PDFTextStripper().getText(doc);
        long nonWhitespaceChars = countNonWhitespace(text);

        if (nonWhitespaceChars == 0) {
            return Assessment.needsOcr(Reason.NO_TEXT);
        }

        if (imageOnlyPageRatio(doc) > minImageOnlyPageRatio) {
            return Assessment.needsOcr(Reason.IMAGE_ONLY);
        }

        return classifyTextDensity(text, nonWhitespaceChars, totalPageArea(doc));
    }

    /**
     * Text-only signals (garbled ratio, chars-per-page-area), split out from
     * {@link #assess} so they're unit-testable with fabricated strings —
     * rendering literal control/replacement chars through a real PDFBox
     * content stream isn't reliable across standard font encodings.
     */
    Assessment classifyTextDensity(String text, long nonWhitespaceChars, double pageArea) {
        long garbledChars = text.chars().filter(PdfComplexityDetector::isGarbledChar).count();
        if ((double) garbledChars / nonWhitespaceChars > maxGarbledRatio) {
            return Assessment.needsOcr(Reason.GARBLED);
        }

        double density = pageArea > 0 ? nonWhitespaceChars / pageArea : Double.MAX_VALUE;
        if (density < minCharsPerPageArea) {
            return Assessment.needsOcr(Reason.SPARSE_TEXT);
        }

        return Assessment.ok();
    }

    private static long countNonWhitespace(String text) {
        return text.chars().filter(c -> !Character.isWhitespace(c)).count();
    }

    private static boolean isGarbledChar(int c) {
        return c == '�' || (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t');
    }

    private double totalPageArea(PDDocument doc) {
        double area = 0;
        for (PDPage page : doc.getPages()) {
            PDRectangle box = page.getMediaBox();
            area += (double) box.getWidth() * box.getHeight();
        }
        return area;
    }

    private double imageOnlyPageRatio(PDDocument doc) throws IOException {
        int totalPages = doc.getNumberOfPages();
        if (totalPages == 0) {
            return 0;
        }
        int imageOnlyPages = 0;
        PDFTextStripper perPageStripper = new PDFTextStripper();
        for (int i = 1; i <= totalPages; i++) {
            PDPage page = doc.getPage(i - 1);
            if (!pageHasImageXObject(page)) {
                continue;
            }
            perPageStripper.setStartPage(i);
            perPageStripper.setEndPage(i);
            long pageChars = countNonWhitespace(perPageStripper.getText(doc));
            if (pageChars == 0) {
                imageOnlyPages++;
            }
        }
        return (double) imageOnlyPages / totalPages;
    }

    private boolean pageHasImageXObject(PDPage page) throws IOException {
        PDResources resources = page.getResources();
        if (resources == null) {
            return false;
        }
        for (COSName name : resources.getXObjectNames()) {
            if (resources.getXObject(name) instanceof PDImageXObject) {
                return true;
            }
        }
        return false;
    }
}
