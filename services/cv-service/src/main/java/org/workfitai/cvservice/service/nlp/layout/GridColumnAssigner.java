package org.workfitai.cvservice.service.nlp.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Assigns each line's segments to a stable column rank by snapping the
 * segment's start X to the nearest previously-detected LEFT anchor (forward
 * anchor propagation: once {@link AnchorDetector} has established column
 * anchors from the whole page, every line's segments snap to those same
 * anchors instead of drifting independently row-to-row — this is what keeps
 * sidebar/main-body or table columns from bleeding into each other).
 * Segments that don't land near any anchor are left "floating" (unanchored).
 */
public class GridColumnAssigner {

    /** How close (in medianCharWidth units) a segment start must be to an anchor to snap to it. */
    private static final double SNAP_TOLERANCE_FACTOR = 1.5;

    public record AssignedSegment(Segment segment, int columnRank, boolean anchored) {
    }

    public record AssignedLine(float y, List<AssignedSegment> segments) {
    }

    public List<AssignedLine> assign(List<Line> lines, List<AnchorDetector.Anchor> anchors, double medianCharWidth) {
        List<AnchorDetector.Anchor> leftAnchors = anchors.stream()
                .filter(a -> a.type() == AnchorDetector.AnchorType.LEFT)
                .sorted(Comparator.comparingDouble(AnchorDetector.Anchor::x))
                .toList();

        double snapTolerance = medianCharWidth * SNAP_TOLERANCE_FACTOR;

        List<AssignedLine> result = new ArrayList<>(lines.size());
        for (Line line : lines) {
            List<AssignedSegment> assigned = new ArrayList<>(line.segments().size());
            for (Segment segment : line.segments()) {
                int rank = nearestAnchorRank(leftAnchors, segment.startX(), snapTolerance);
                assigned.add(new AssignedSegment(segment, rank, rank >= 0));
            }
            result.add(new AssignedLine(line.y(), assigned));
        }
        return result;
    }

    private int nearestAnchorRank(List<AnchorDetector.Anchor> leftAnchors, double startX, double tolerance) {
        int bestRank = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < leftAnchors.size(); i++) {
            double distance = Math.abs(leftAnchors.get(i).x() - startX);
            if (distance <= tolerance && distance < bestDistance) {
                bestDistance = distance;
                bestRank = i;
            }
        }
        return bestRank;
    }
}
