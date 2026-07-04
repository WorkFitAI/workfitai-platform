package org.workfitai.cvservice.service.nlp.layout;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Escape hatch for flowing/paragraph text where column structure would be
 * noise, not signal (e.g. justified prose with ragged line starts). Joins each
 * line's segments left-to-right with a space, same shape as the plain
 * Y-then-X text the pipeline used to always produce — this keeps
 * single-column CVs behaving exactly as before when grid projection isn't
 * warranted.
 */
public class PlainJoiner {

    /** Blocks with this many anchors or fewer are candidates for the escape hatch. */
    private static final int MAX_ANCHORS_FOR_ESCAPE_HATCH = 4;

    /**
     * True when the block looks like flowing prose rather than a structured
     * column/table layout: few anchors, and most lines span more than half the
     * page width (per liteparse's escape-hatch condition).
     *
     * <p>Only LEFT anchors are counted here — matching what {@link GridColumnAssigner}
     * actually uses for column ranking. A single-column CV with right-aligned or
     * centered content (e.g. a job title on the same line as a right-aligned
     * date range) can easily accumulate many RIGHT/CENTER anchors from ordinary
     * text-edge alignment; counting those would push a genuinely flowing,
     * single-column document past the anchor threshold and into full
     * grid-projection for no reason, when the real column signal (RIGHT/CENTER
     * anchors) isn't even used for anything once grid-projection runs.
     *
     * <p>"Wide" only counts single-segment lines whose own text run is long —
     * a multi-segment row (e.g. a sidebar phrase and a main-body phrase at the
     * same Y) can have a wide overall {@link Line#span()} purely because of the
     * gap *between* two short segments, which is exactly the columnar signal
     * this escape hatch must NOT be fooled by.
     */
    public boolean shouldUseEscapeHatch(List<AnchorDetector.Anchor> anchors, List<Line> lines, double pageWidth) {
        if (lines.isEmpty()) {
            return true;
        }
        long leftAnchorCount = anchors.stream().filter(a -> a.type() == AnchorDetector.AnchorType.LEFT).count();
        if (leftAnchorCount > MAX_ANCHORS_FOR_ESCAPE_HATCH) {
            return false;
        }
        double halfPageWidth = pageWidth / 2.0;
        long wideLines = lines.stream()
                .filter(line -> line.segments().size() == 1 && line.span() > halfPageWidth)
                .count();
        return wideLines > lines.size() / 2.0;
    }

    public String join(List<Line> lines) {
        return lines.stream()
                .map(line -> line.segments().stream()
                        .map(Segment::text)
                        .collect(Collectors.joining(" ")))
                .collect(Collectors.joining("\n"));
    }
}
