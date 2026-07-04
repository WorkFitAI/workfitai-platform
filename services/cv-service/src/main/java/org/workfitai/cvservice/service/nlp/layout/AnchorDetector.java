package org.workfitai.cvservice.service.nlp.layout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Detects left/right/center "anchors" — X positions where segment edges align
 * across multiple lines (a column start, a right-aligned date, a centered
 * header). Anchors are rounded to a quarter of the median char width to
 * tolerate glyph-position jitter, then only kept if enough distinct lines
 * agree on them (drops isolated/one-off alignments).
 */
public class AnchorDetector {

    /** An anchor needs at least this many lines aligning to it to count as real structure, not coincidence. */
    private static final int MIN_SUPPORT = 2;

    public enum AnchorType {
        LEFT, RIGHT, CENTER
    }

    public record Anchor(AnchorType type, double x, int support) {
    }

    /**
     * @param lines           lines already grouped/segmented by {@link LineGrouper}
     * @param medianCharWidth page-level median glyph width, used both as the anchor-rounding unit and self-calibrating grid cell size
     */
    public List<Anchor> detectAnchors(List<Line> lines, double medianCharWidth) {
        if (lines.isEmpty() || medianCharWidth <= 0) {
            return List.of();
        }
        double unit = medianCharWidth / 4.0;

        Map<Double, Integer> leftCounts = new TreeMap<>();
        Map<Double, Integer> rightCounts = new TreeMap<>();
        Map<Double, Integer> centerCounts = new TreeMap<>();

        for (Line line : lines) {
            // Dedupe per line: two segments on the SAME line coincidentally
            // rounding to the same value (e.g. double-rendered/"fake-bold"
            // glyphs) must count as one line agreeing, not two, so MIN_SUPPORT
            // genuinely means "at least this many distinct lines".
            Set<Double> leftInLine = new HashSet<>();
            Set<Double> rightInLine = new HashSet<>();
            Set<Double> centerInLine = new HashSet<>();
            for (Segment segment : line.segments()) {
                leftInLine.add(LayoutMath.roundToUnit(segment.startX(), unit));
                rightInLine.add(LayoutMath.roundToUnit(segment.endX(), unit));
                centerInLine.add(LayoutMath.roundToUnit((segment.startX() + segment.endX()) / 2.0, unit));
            }
            leftInLine.forEach(x -> bump(leftCounts, x));
            rightInLine.forEach(x -> bump(rightCounts, x));
            centerInLine.forEach(x -> bump(centerCounts, x));
        }

        List<Anchor> anchors = new ArrayList<>();
        collectAnchors(leftCounts, AnchorType.LEFT, anchors);
        collectAnchors(rightCounts, AnchorType.RIGHT, anchors);
        collectAnchors(centerCounts, AnchorType.CENTER, anchors);
        return anchors;
    }

    private void collectAnchors(Map<Double, Integer> counts, AnchorType type, List<Anchor> out) {
        for (Map.Entry<Double, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= MIN_SUPPORT) {
                out.add(new Anchor(type, entry.getKey(), entry.getValue()));
            }
        }
    }

    private static void bump(Map<Double, Integer> counts, double key) {
        counts.merge(key, 1, Integer::sum);
    }
}
