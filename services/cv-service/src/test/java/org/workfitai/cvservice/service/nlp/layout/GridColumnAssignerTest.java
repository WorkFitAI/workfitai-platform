package org.workfitai.cvservice.service.nlp.layout;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GridColumnAssignerTest {

    private final GridColumnAssigner assigner = new GridColumnAssigner();

    private static Line lineWithSegments(float y, Segment... segments) {
        return new Line(y, List.of(segments));
    }

    private static Segment seg(float startX, float endX, String text) {
        return new Segment(startX, endX, text);
    }

    @Test
    void tableLikeInput_assignsConsistentColumnRankAcrossRows() {
        // 3 fixed columns (x=50,200,350) across 3 rows — column assignment must
        // stay stable row-to-row, exactly the table/grid case this phase fixes.
        List<AnchorDetector.Anchor> anchors = List.of(
                new AnchorDetector.Anchor(AnchorDetector.AnchorType.LEFT, 50, 3),
                new AnchorDetector.Anchor(AnchorDetector.AnchorType.LEFT, 200, 3),
                new AnchorDetector.Anchor(AnchorDetector.AnchorType.LEFT, 350, 3));

        List<Line> lines = List.of(
                lineWithSegments(700, seg(50, 90, "Java"), seg(200, 240, "React"), seg(350, 400, "Docker")),
                lineWithSegments(685, seg(50, 110, "PostgreSQL"), seg(200, 240, "Kafka"), seg(350, 390, "Redis")),
                lineWithSegments(670, seg(50, 120, "MongoDB"), seg(200, 260, "TypeScript"), seg(350, 400, "GraphQL")));

        List<GridColumnAssigner.AssignedLine> assigned = assigner.assign(lines, anchors, 6.0);

        for (GridColumnAssigner.AssignedLine line : assigned) {
            assertThat(line.segments()).hasSize(3);
            assertThat(line.segments().get(0).columnRank()).isZero();
            assertThat(line.segments().get(1).columnRank()).isEqualTo(1);
            assertThat(line.segments().get(2).columnRank()).isEqualTo(2);
            assertThat(line.segments()).allMatch(GridColumnAssigner.AssignedSegment::anchored);
        }
    }

    @Test
    void segmentFarFromAnyAnchor_isFloatingUnanchored() {
        List<AnchorDetector.Anchor> anchors = List.of(
                new AnchorDetector.Anchor(AnchorDetector.AnchorType.LEFT, 50, 3));

        List<Line> lines = List.of(lineWithSegments(700, seg(500, 550, "Stray")));

        List<GridColumnAssigner.AssignedLine> assigned = assigner.assign(lines, anchors, 6.0);

        assertThat(assigned.get(0).segments().get(0).anchored()).isFalse();
        assertThat(assigned.get(0).segments().get(0).columnRank()).isEqualTo(-1);
    }

    @Test
    void noAnchors_allSegmentsAreFloating() {
        List<Line> lines = List.of(lineWithSegments(700, seg(50, 90, "Solo")));

        List<GridColumnAssigner.AssignedLine> assigned = assigner.assign(lines, List.of(), 6.0);

        assertThat(assigned.get(0).segments().get(0).anchored()).isFalse();
    }
}
