package org.workfitai.cvservice.service.nlp.layout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * In-house Java port of liteparse's grid-projection idea, built directly on
 * PDFBox {@link TextPosition} data. Replaces the naive
 * {@code setSortByPosition(true)} stripper (which merges same-height,
 * different-column text into one interleaved line, burying section headers
 * where {@code parseCvText()} can never recognize them) with column-aware
 * reconstruction: group glyphs into Y-rows, split rows into segments on wide
 * gaps, detect column anchors, snap each segment to a stable column, then
 * emit one full column at a time (or fall back to plain space-joining for
 * flowing paragraph text where column structure would be noise).
 *
 * <p>Per-page pipeline, driven by the {@link #processTextPosition} /
 * {@link #endPage} hooks: {@link LineGrouper} → {@link AnchorDetector} →
 * escape-hatch check ({@link PlainJoiner}) → {@link GridColumnAssigner} +
 * {@link ReadingOrderSorter}. The base class's own text-assembly
 * ({@link #writeString}) is suppressed since this class builds its own output
 * from the collected positions instead.
 */
public class GridProjectionTextExtractor extends PDFTextStripper {

    private final LineGrouper lineGrouper = new LineGrouper();
    private final AnchorDetector anchorDetector = new AnchorDetector();
    private final GridColumnAssigner columnAssigner = new GridColumnAssigner();
    private final ReadingOrderSorter readingOrderSorter = new ReadingOrderSorter();
    private final PlainJoiner plainJoiner = new PlainJoiner();

    private final List<CharPosition> currentPageChars = new ArrayList<>();
    private final StringBuilder output = new StringBuilder();

    public GridProjectionTextExtractor() throws IOException {
        super();
    }

    /** Extracts column-aware plain text from {@code doc}. Discards the base class's own {@link #getText} return value. */
    public String extract(PDDocument doc) throws IOException {
        output.setLength(0);
        currentPageChars.clear();
        super.getText(doc);
        return output.toString();
    }

    @Override
    protected void processTextPosition(TextPosition text) {
        currentPageChars.add(new CharPosition(text.getX(), text.getY(), text.getWidth(), text.getHeight(),
                text.getUnicode()));
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) {
        // No-op: suppress the base class's own Y-then-X text assembly. This
        // class builds its own output from processTextPosition()'s collected
        // positions in endPage() instead.
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
        if (!currentPageChars.isEmpty()) {
            String pageText = renderPage(currentPageChars, page.getMediaBox().getWidth());
            if (output.length() > 0) {
                output.append('\n');
            }
            output.append(pageText);
        }
        currentPageChars.clear();
        super.endPage(page);
    }

    private String renderPage(List<CharPosition> chars, double pageWidth) {
        double medianHeight = LayoutMath.median(chars.stream().map(c -> (double) c.height()).toList());
        double medianCharWidth = LayoutMath.median(chars.stream().map(c -> (double) c.width()).toList());
        double yTolerance = Math.max(medianHeight * 0.5, 5.0);

        List<Line> lines = lineGrouper.group(chars, yTolerance, medianCharWidth);
        List<AnchorDetector.Anchor> anchors = anchorDetector.detectAnchors(lines, medianCharWidth);

        if (plainJoiner.shouldUseEscapeHatch(anchors, lines, pageWidth)) {
            return plainJoiner.join(lines);
        }

        List<GridColumnAssigner.AssignedLine> assigned = columnAssigner.assign(lines, anchors, medianCharWidth);
        return readingOrderSorter.buildReadingOrder(assigned);
    }
}
