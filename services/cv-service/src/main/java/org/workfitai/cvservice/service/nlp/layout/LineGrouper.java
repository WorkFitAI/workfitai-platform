package org.workfitai.cvservice.service.nlp.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Groups raw per-glyph {@link CharPosition}s into {@link Line}s (Y-rows), then
 * within each row splits characters into {@link Segment}s wherever the
 * horizontal gap between consecutive glyphs is wide enough to signal a real
 * column break (e.g. sidebar text vs. main-body text sitting at the same page
 * height) rather than ordinary letter/word spacing.
 */
public class LineGrouper {

    /** Gaps wider than this multiple of medianCharWidth start a new segment (column break). */
    private static final double SEGMENT_GAP_FACTOR = 3.0;

    /** Gaps wider than this multiple of medianCharWidth (but narrower than the segment gap) become a literal space. */
    private static final double SPACE_GAP_FACTOR = 0.3;

    /**
     * @param chars           all glyphs on the page, any order
     * @param yTolerance      max Y difference (pt) for two glyphs to be considered the same row —
     *                        per liteparse's formula: {@code max(medianHeight * 0.5, 5.0)}
     * @param medianCharWidth page-level median glyph width, used to size the segment/space gap thresholds
     */
    public List<Line> group(List<CharPosition> chars, double yTolerance, double medianCharWidth) {
        if (chars.isEmpty()) {
            return List.of();
        }

        List<CharPosition> sortedByY = chars.stream()
                .sorted(Comparator.comparingDouble(CharPosition::y))
                .toList();

        List<List<CharPosition>> rows = groupIntoRows(sortedByY, yTolerance);

        List<Line> lines = new ArrayList<>(rows.size());
        for (List<CharPosition> row : rows) {
            lines.add(buildLine(row, medianCharWidth));
        }
        return lines;
    }

    private List<List<CharPosition>> groupIntoRows(List<CharPosition> sortedByY, double yTolerance) {
        List<List<CharPosition>> rows = new ArrayList<>();
        List<CharPosition> currentRow = new ArrayList<>();
        // Compared against the row's FIRST (anchor) Y, not a running average —
        // a running average lets a chain of glyphs each within tolerance of the
        // *current* average still drift the row's overall Y span past yTolerance.
        double rowAnchorY = 0;

        for (CharPosition c : sortedByY) {
            if (currentRow.isEmpty()) {
                currentRow.add(c);
                rowAnchorY = c.y();
                continue;
            }
            if (Math.abs(c.y() - rowAnchorY) <= yTolerance) {
                currentRow.add(c);
            } else {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
                currentRow.add(c);
                rowAnchorY = c.y();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }
        return rows;
    }

    private Line buildLine(List<CharPosition> row, double medianCharWidth) {
        List<CharPosition> sortedByX = row.stream()
                .sorted(Comparator.comparingDouble(CharPosition::x))
                .toList();

        double avgY = sortedByX.stream().mapToDouble(CharPosition::y).average().orElse(0);
        double segmentGapThreshold = medianCharWidth * SEGMENT_GAP_FACTOR;
        double spaceGapThreshold = medianCharWidth * SPACE_GAP_FACTOR;

        List<Segment> segments = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        double segmentStartX = sortedByX.get(0).x();
        double prevEndX = segmentStartX;

        for (CharPosition c : sortedByX) {
            double gap = c.x() - prevEndX;
            if (text.length() > 0 && gap > segmentGapThreshold) {
                segments.add(new Segment((float) segmentStartX, (float) prevEndX, text.toString()));
                text = new StringBuilder();
                segmentStartX = c.x();
            } else if (text.length() > 0 && gap > spaceGapThreshold) {
                text.append(' ');
            }
            // CID/embedded fonts without a ToUnicode map can yield a null unicode
            // mapping — append nothing rather than the literal string "null".
            if (c.text() != null) {
                text.append(c.text());
            }
            prevEndX = c.x() + c.width();
        }
        if (text.length() > 0) {
            segments.add(new Segment((float) segmentStartX, (float) prevEndX, text.toString()));
        }

        return new Line((float) avgY, segments);
    }
}
