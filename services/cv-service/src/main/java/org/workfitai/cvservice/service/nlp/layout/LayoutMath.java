package org.workfitai.cvservice.service.nlp.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small shared numeric helpers for the grid-projection layout classes. */
final class LayoutMath {

    private LayoutMath() {
    }

    static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
        }
        return sorted.get(mid);
    }

    /** Rounds {@code value} to the nearest multiple of {@code unit} (unit must be > 0). */
    static double roundToUnit(double value, double unit) {
        return Math.round(value / unit) * unit;
    }
}
