package org.workfitai.cvservice.service.nlp.layout;

/**
 * A contiguous run of text within one Y-row, split from neighboring runs by a
 * horizontal gap wider than the segment-gap threshold (see {@link LineGrouper}).
 * One row can hold multiple segments — e.g. a sidebar phrase and a main-body
 * phrase sitting at the same page height in a two-column CV.
 */
public record Segment(float startX, float endX, String text) {
}
