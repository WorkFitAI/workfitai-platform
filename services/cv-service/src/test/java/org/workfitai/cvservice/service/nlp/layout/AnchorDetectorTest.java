package org.workfitai.cvservice.service.nlp.layout;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnchorDetectorTest {

    private final AnchorDetector detector = new AnchorDetector();

    private static Line lineWithSegments(float y, Segment... segments) {
        return new Line(y, List.of(segments));
    }

    private static Segment seg(float startX, float endX, String text) {
        return new Segment(startX, endX, text);
    }

    @Test
    void twoColumnLayout_detectsTwoLeftAnchors() {
        // Left column consistently starts at x=50, right column at x=280, across 4 rows.
        List<Line> lines = List.of(
                lineWithSegments(700, seg(50, 90, "John Doe"), seg(280, 340, "Experience")),
                lineWithSegments(685, seg(50, 90, "Skills"), seg(280, 340, "Senior Eng")),
                lineWithSegments(670, seg(50, 90, "Java"), seg(280, 340, "Built things")),
                lineWithSegments(655, seg(50, 90, "React"), seg(280, 340, "Led team")));

        List<AnchorDetector.Anchor> anchors = detector.detectAnchors(lines, 6.0);

        List<AnchorDetector.Anchor> leftAnchors = anchors.stream()
                .filter(a -> a.type() == AnchorDetector.AnchorType.LEFT)
                .toList();
        // Anchors are rounded to the nearest quarter-unit (unit = medianCharWidth/4 = 1.5),
        // so exact input x=50/280 land on the nearest 1.5-multiple: 49.5/280.5.
        assertThat(leftAnchors).hasSize(2);
        assertThat(leftAnchors).anyMatch(a -> Math.abs(a.x() - 50) < 1.0 && a.support() == 4);
        assertThat(leftAnchors).anyMatch(a -> Math.abs(a.x() - 280) < 1.0 && a.support() == 4);
    }

    @Test
    void singleColumnParagraph_hasFewAnchors() {
        // Left-aligned paragraph — one consistent left anchor, no second column.
        List<Line> lines = List.of(
                lineWithSegments(700, seg(50, 400, "Backend developer with five years experience")),
                lineWithSegments(685, seg(50, 380, "building scalable distributed systems for")),
                lineWithSegments(670, seg(50, 200, "the payments platform.")));

        List<AnchorDetector.Anchor> anchors = detector.detectAnchors(lines, 6.0);

        List<AnchorDetector.Anchor> leftAnchors = anchors.stream()
                .filter(a -> a.type() == AnchorDetector.AnchorType.LEFT)
                .toList();
        assertThat(leftAnchors).hasSize(1);
        assertThat(anchors).hasSizeLessThanOrEqualTo(4);
    }

    @Test
    void isolatedOneOffAlignment_isNotAnAnchor() {
        // Only one line starts at x=200 — not enough support (MIN_SUPPORT=2) to count as a real anchor.
        List<Line> lines = List.of(
                lineWithSegments(700, seg(50, 90, "A")),
                lineWithSegments(685, seg(50, 90, "B")),
                lineWithSegments(670, seg(200, 240, "C")));

        List<AnchorDetector.Anchor> anchors = detector.detectAnchors(lines, 6.0);

        assertThat(anchors).noneMatch(a -> Math.abs(a.x() - 200) < 0.01);
    }

    @Test
    void emptyLines_returnsNoAnchors() {
        assertThat(detector.detectAnchors(List.of(), 6.0)).isEmpty();
    }
}
