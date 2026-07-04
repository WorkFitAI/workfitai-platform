package org.workfitai.cvservice.service.nlp.layout;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlainJoinerTest {

    private final PlainJoiner joiner = new PlainJoiner();

    private static Line lineWithSegments(float y, Segment... segments) {
        return new Line(y, List.of(segments));
    }

    private static Segment seg(float startX, float endX, String text) {
        return new Segment(startX, endX, text);
    }

    @Test
    void flowingParagraph_fewAnchorsAndWideLines_triggersEscapeHatch() {
        // Single left anchor, every line spans most of a 612pt-wide page — flowing prose.
        List<AnchorDetector.Anchor> anchors = List.of(
                new AnchorDetector.Anchor(AnchorDetector.AnchorType.LEFT, 50, 3));
        List<Line> lines = List.of(
                lineWithSegments(100, seg(50, 550, "Backend developer with five years experience")),
                lineWithSegments(115, seg(50, 540, "building scalable distributed systems for")),
                lineWithSegments(130, seg(50, 520, "the payments platform end to end.")));

        assertThat(joiner.shouldUseEscapeHatch(anchors, lines, 612.0)).isTrue();
    }

    @Test
    void columnarLayout_manyAnchorsOrNarrowLines_doesNotTriggerEscapeHatch() {
        // 3 anchors (3 columns) with narrow per-cell lines — structured table, not prose.
        List<AnchorDetector.Anchor> anchors = List.of(
                new AnchorDetector.Anchor(AnchorDetector.AnchorType.LEFT, 50, 3),
                new AnchorDetector.Anchor(AnchorDetector.AnchorType.LEFT, 200, 3),
                new AnchorDetector.Anchor(AnchorDetector.AnchorType.LEFT, 350, 3),
                new AnchorDetector.Anchor(AnchorDetector.AnchorType.LEFT, 450, 3),
                new AnchorDetector.Anchor(AnchorDetector.AnchorType.LEFT, 500, 3));
        List<Line> lines = List.of(
                lineWithSegments(100, seg(50, 90, "Java"), seg(200, 240, "React"), seg(350, 400, "Docker")),
                lineWithSegments(115, seg(50, 110, "PostgreSQL"), seg(200, 240, "Kafka"), seg(350, 390, "Redis")));

        assertThat(joiner.shouldUseEscapeHatch(anchors, lines, 612.0)).isFalse();
    }

    @Test
    void twoColumnRowsWithWideGapBetweenSegments_doesNotTriggerEscapeHatch() {
        // Each row has 2 short segments (left ~x=50-90, right ~x=280-380) with a
        // big gap between them, making the overall Line.span() wide purely from
        // that gap — must NOT be mistaken for one long flowing-prose line.
        List<AnchorDetector.Anchor> anchors = List.of(
                new AnchorDetector.Anchor(AnchorDetector.AnchorType.LEFT, 50, 3),
                new AnchorDetector.Anchor(AnchorDetector.AnchorType.LEFT, 280, 3));
        List<Line> lines = List.of(
                lineWithSegments(100, seg(50, 90, "Skills"), seg(280, 380, "Experience")),
                lineWithSegments(115, seg(50, 90, "Java"), seg(280, 380, "Senior Engineer")),
                lineWithSegments(130, seg(50, 90, "React"), seg(280, 380, "Built things")));

        assertThat(joiner.shouldUseEscapeHatch(anchors, lines, 612.0)).isFalse();
    }

    @Test
    void emptyLines_triggersEscapeHatch() {
        assertThat(joiner.shouldUseEscapeHatch(List.of(), List.of(), 612.0)).isTrue();
    }

    @Test
    void join_joinsSegmentsWithSpaceAndLinesWithNewline() {
        List<Line> lines = List.of(
                lineWithSegments(100, seg(50, 90, "Hello"), seg(120, 160, "world")),
                lineWithSegments(115, seg(50, 90, "Second"), seg(120, 160, "line")));

        String result = joiner.join(lines);

        assertThat(result).isEqualTo("Hello world\nSecond line");
    }
}
