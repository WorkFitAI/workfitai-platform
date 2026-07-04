package org.workfitai.cvservice.service.nlp.layout;

import java.util.List;

/** One Y-row of the page, made of one or more horizontally-separated {@link Segment}s. */
public record Line(float y, List<Segment> segments) {

    /** Horizontal span from the leftmost segment start to the rightmost segment end. */
    public double span() {
        if (segments.isEmpty()) {
            return 0;
        }
        double minX = segments.stream().mapToDouble(Segment::startX).min().orElse(0);
        double maxX = segments.stream().mapToDouble(Segment::endX).max().orElse(0);
        return maxX - minX;
    }
}
