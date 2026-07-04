package org.workfitai.cvservice.service.nlp.layout;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingOrderSorterTest {

    private final ReadingOrderSorter sorter = new ReadingOrderSorter();

    private static GridColumnAssigner.AssignedLine line(float y, GridColumnAssigner.AssignedSegment... segments) {
        return new GridColumnAssigner.AssignedLine(y, List.of(segments));
    }

    private static GridColumnAssigner.AssignedSegment anchored(float startX, String text, int rank) {
        return new GridColumnAssigner.AssignedSegment(new Segment(startX, startX + 40, text), rank, true);
    }

    private static GridColumnAssigner.AssignedSegment floating(float startX, String text) {
        return new GridColumnAssigner.AssignedSegment(new Segment(startX, startX + 40, text), -1, false);
    }

    @Test
    void twoColumns_emitsEntireLeftColumnBeforeEntireRightColumn() {
        // Naive Y-then-X sort would interleave "Skills"/"Experience" row by row;
        // reading order must emit the whole left (rank 0) column, then the whole
        // right (rank 1) column — this is the actual fix for the reported bug.
        // Ascending Y = top-to-bottom reading order (matches LineGrouper's convention).
        List<GridColumnAssigner.AssignedLine> lines = List.of(
                line(100, anchored(50, "Skills", 0), anchored(280, "Experience", 1)),
                line(115, anchored(50, "Java", 0), anchored(280, "Senior Engineer", 1)),
                line(130, anchored(50, "React", 0), anchored(280, "Built things", 1)));

        String result = sorter.buildReadingOrder(lines);

        assertThat(result.split("\n")).containsExactly(
                "Skills", "Java", "React", "Experience", "Senior Engineer", "Built things");
    }

    @Test
    void floatingSegments_emittedBeforeAnchoredColumns() {
        List<GridColumnAssigner.AssignedLine> lines = List.of(
                line(100, floating(50, "John Doe")),
                line(115, anchored(50, "Skills", 0)),
                line(130, anchored(50, "Java", 0)));

        String result = sorter.buildReadingOrder(lines);

        assertThat(result.split("\n")).containsExactly("John Doe", "Skills", "Java");
    }

    @Test
    void singleColumn_preservesTopToBottomOrder() {
        List<GridColumnAssigner.AssignedLine> lines = List.of(
                line(100, anchored(50, "First", 0)),
                line(115, anchored(50, "Second", 0)),
                line(130, anchored(50, "Third", 0)));

        String result = sorter.buildReadingOrder(lines);

        assertThat(result.split("\n")).containsExactly("First", "Second", "Third");
    }

    @Test
    void emptyInput_returnsEmptyString() {
        assertThat(sorter.buildReadingOrder(List.of())).isEmpty();
    }

    @Test
    void rowWithOneAnchoredAndOneFloatingSegment_staysTogetherInThatColumn() {
        // The critical bug this locks in: a job title (anchored, left margin) on
        // the same row as a right-aligned date (unanchored — its X varies per
        // entry length, so it doesn't reliably snap to any anchor). Previously
        // ALL floating segments were pooled globally to the very front of the
        // whole document, so every date would jump ahead of "John Doe" — this
        // must instead stay attached to its own row, in natural left-to-right order.
        List<GridColumnAssigner.AssignedLine> lines = List.of(
                line(100, floating(50, "John Doe")),
                line(115, anchored(50, "Experience", 0)),
                line(130, anchored(50, "Senior Backend Engineer", 0), floating(500, "2020 - Present")),
                line(145, anchored(50, "Backend Engineer", 0), floating(510, "2018 - 2020")));

        String result = sorter.buildReadingOrder(lines);

        assertThat(result.split("\n")).containsExactly(
                "John Doe",
                "Experience",
                "Senior Backend Engineer 2020 - Present",
                "Backend Engineer 2018 - 2020");
    }

    @Test
    void mixedRowAndGenuineTwoColumnRows_bothHandledCorrectly() {
        // A row with 1 anchored + 1 floating segment (title+date) must stay
        // together, while a row with 2 DIFFERENT anchored columns (real sidebar
        // vs. main-body content) must still split into separate column blocks.
        List<GridColumnAssigner.AssignedLine> lines = List.of(
                line(100, anchored(50, "Skills", 0), anchored(280, "Experience", 1)),
                line(115, anchored(50, "Java", 0)),
                line(130, anchored(280, "Senior Engineer", 1), floating(560, "2020 - Present")));

        String result = sorter.buildReadingOrder(lines);

        assertThat(result.split("\n")).containsExactly(
                "Skills", "Java", "Experience", "Senior Engineer 2020 - Present");
    }
}
