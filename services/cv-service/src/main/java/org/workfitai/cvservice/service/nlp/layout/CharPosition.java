package org.workfitai.cvservice.service.nlp.layout;

/**
 * Minimal per-glyph position data lifted from PDFBox's {@code TextPosition},
 * kept as our own lightweight type so the layout-reconstruction math
 * (LineGrouper/AnchorDetector/GridColumnAssigner/ReadingOrderSorter) is
 * unit-testable with synthetic values, independent of PDFBox's own
 * hard-to-construct {@code TextPosition} objects.
 */
public record CharPosition(float x, float y, float width, float height, String text) {
}
