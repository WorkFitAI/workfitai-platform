package org.workfitai.cvservice.service.nlp.layout;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LineGrouperTest {

    private final LineGrouper grouper = new LineGrouper();

    private static CharPosition c(float x, float y, String text) {
        return new CharPosition(x, y, 6f, 10f, text);
    }

    @Test
    void closeTogetherChars_mergeIntoOneLine() {
        // Y values within tolerance (yTolerance=5) must merge into a single row.
        List<CharPosition> chars = List.of(
                c(50, 100.0f, "A"),
                c(56, 101.0f, "B"),
                c(62, 102.0f, "C"));

        List<Line> lines = grouper.group(chars, 5.0, 6.0);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).segments()).hasSize(1);
        assertThat(lines.get(0).segments().get(0).text()).isEqualTo("ABC");
    }

    @Test
    void charsExactlyAtToleranceBoundary_stillMerge() {
        // gap == yTolerance is inclusive ("<=" in the grouping logic).
        List<CharPosition> chars = List.of(
                c(50, 100.0f, "A"),
                c(56, 105.0f, "B"));

        List<Line> lines = grouper.group(chars, 5.0, 6.0);

        assertThat(lines).hasSize(1);
    }

    @Test
    void charsBeyondToleranceBoundary_splitIntoSeparateLines() {
        List<CharPosition> chars = List.of(
                c(50, 100.0f, "A"),
                c(56, 105.1f, "B"));

        List<Line> lines = grouper.group(chars, 5.0, 6.0);

        assertThat(lines).hasSize(2);
    }

    @Test
    void wideGapWithinRow_splitsIntoTwoSegments() {
        // Sidebar text (x=50) and main-body text (x=280) at the same Y — the exact
        // bug this phase fixes: must NOT be merged into one interleaved string.
        List<CharPosition> chars = List.of(
                c(50, 100f, "L"), c(56, 100f, "e"), c(62, 100f, "f"), c(68, 100f, "t"),
                c(280, 100f, "R"), c(286, 100f, "i"), c(292, 100f, "g"), c(298, 100f, "h"), c(304, 100f, "t"));

        List<Line> lines = grouper.group(chars, 5.0, 6.0);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).segments()).hasSize(2);
        assertThat(lines.get(0).segments().get(0).text()).isEqualTo("Left");
        assertThat(lines.get(0).segments().get(1).text()).isEqualTo("Right");
    }

    @Test
    void moderateGap_insertsSpaceWithoutSplittingSegment() {
        // A normal inter-word gap (bigger than a glued glyph gap, smaller than the
        // column-break threshold) should become a literal space within one segment.
        List<CharPosition> chars = List.of(
                c(50, 100f, "H"), c(56, 100f, "i"),
                c(64, 100f, "T"), c(70, 100f, "h"), c(76, 100f, "e"), c(82, 100f, "r"), c(88, 100f, "e"));

        List<Line> lines = grouper.group(chars, 5.0, 6.0);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).segments()).hasSize(1);
        assertThat(lines.get(0).segments().get(0).text()).isEqualTo("Hi There");
    }

    @Test
    void emptyInput_returnsNoLines() {
        assertThat(grouper.group(List.of(), 5.0, 6.0)).isEmpty();
    }
}
