package org.workfitai.cvservice.service.nlp.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.workfitai.cvservice.service.nlp.layout.GridColumnAssigner.AssignedLine;
import org.workfitai.cvservice.service.nlp.layout.GridColumnAssigner.AssignedSegment;

/**
 * Reassembles column-assigned lines into plain text in liteparse's reading-order
 * priority: floating (unanchored) rows first, then each anchored column
 * emitted as its own top-to-bottom block, columns ordered left-to-right by
 * rank. This is the actual fix for cross-column bleed: a naive Y-then-X sort
 * interleaves sidebar and main-body text row by row, burying section headers
 * mid-line where {@code parseCvText()} can never recognize them; emitting one
 * full column at a time keeps each column's own header/content sequence intact.
 *
 * <p>Grouping happens per ROW, not per segment: a row is only split across
 * multiple column blocks when it genuinely touches 2+ *different* anchored
 * columns (the real cross-column case). A row with exactly one anchored
 * column plus an incidental unanchored segment — e.g. a job title (anchored,
 * left margin) followed by a right-aligned date (unanchored, no stable
 * column) — stays together as one line in that column's block instead of the
 * date being torn out and pooled into the page-wide floating block. Without
 * this, a single-column CV with inline dates would have every date scrambled
 * to the very front of the whole document, ahead of the person's name.
 */
public class ReadingOrderSorter {

    public String buildReadingOrder(List<AssignedLine> lines) {
        List<RowEntry> floatingRows = new ArrayList<>();
        var byColumn = new TreeMap<Integer, List<RowEntry>>();

        for (AssignedLine line : lines) {
            List<Integer> distinctRanks = line.segments().stream()
                    .filter(AssignedSegment::anchored)
                    .map(AssignedSegment::columnRank)
                    .distinct()
                    .sorted()
                    .toList();

            if (distinctRanks.isEmpty()) {
                // Entire row is unanchored (e.g. a name/header before any column is established).
                for (AssignedSegment segment : line.segments()) {
                    floatingRows.add(new RowEntry(line.y(), segment.segment().startX(), segment.segment().text()));
                }
            } else if (distinctRanks.size() == 1) {
                // One column touches this row; keep the whole row (including any
                // incidental unanchored segment) together, in natural X order.
                String rowText = line.segments().stream()
                        .sorted(Comparator.comparingDouble(s -> s.segment().startX()))
                        .map(s -> s.segment().text())
                        .collect(Collectors.joining(" "));
                byColumn.computeIfAbsent(distinctRanks.get(0), k -> new ArrayList<>())
                        .add(new RowEntry(line.y(), line.segments().get(0).segment().startX(), rowText));
            } else {
                // Genuine multi-column row: split each segment into its own column's
                // block. Any unanchored segment sharing this row is rare enough to
                // just pool with the page-wide floating rows.
                for (AssignedSegment segment : line.segments()) {
                    RowEntry entry = new RowEntry(line.y(), segment.segment().startX(), segment.segment().text());
                    if (segment.anchored()) {
                        byColumn.computeIfAbsent(segment.columnRank(), k -> new ArrayList<>()).add(entry);
                    } else {
                        floatingRows.add(entry);
                    }
                }
            }
        }

        List<String> outputLines = new ArrayList<>();
        outputLines.addAll(orderedText(floatingRows));
        for (List<RowEntry> column : byColumn.values()) {
            outputLines.addAll(orderedText(column));
        }
        return String.join("\n", outputLines);
    }

    private record RowEntry(float y, float x, String text) {
    }

    private List<String> orderedText(List<RowEntry> rows) {
        return rows.stream()
                .sorted(Comparator.comparingDouble(RowEntry::y).thenComparingDouble(RowEntry::x))
                .map(RowEntry::text)
                .collect(Collectors.toList());
    }
}
