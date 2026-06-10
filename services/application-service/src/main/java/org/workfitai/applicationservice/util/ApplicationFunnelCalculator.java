package org.workfitai.applicationservice.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculates conversion rates between funnel stages from a status-count map.
 * Shared by ManagerStatsService and SystemStatsService to avoid duplication.
 */
public final class ApplicationFunnelCalculator {

    private ApplicationFunnelCalculator() {}

    /**
     * Computes stage-to-stage conversion rates.
     * byStatus must contain "ever been in" counts (each application counted once per stage
     * it has passed through), not just current-state snapshots.
     * Rate = count(ever reached next stage) / count(ever reached this stage).
     */
    public static Map<String, Double> calculateFunnelRates(Map<String, Long> byStatus) {
        long applied   = getCount(byStatus, "APPLIED");
        long reviewing = getCount(byStatus, "REVIEWING");
        long interview = getCount(byStatus, "INTERVIEW");
        long offer     = getCount(byStatus, "OFFER");
        long hired     = getCount(byStatus, "HIRED");

        Map<String, Double> rates = new HashMap<>();
        rates.put("APPLIED_TO_REVIEWING",   applied   > 0 ? (double) reviewing / applied   : 0.0);
        rates.put("REVIEWING_TO_INTERVIEW", reviewing > 0 ? (double) interview / reviewing : 0.0);
        rates.put("INTERVIEW_TO_OFFER",     interview > 0 ? (double) offer     / interview : 0.0);
        rates.put("OFFER_TO_HIRED",         offer     > 0 ? (double) hired     / offer     : 0.0);
        return rates;
    }

    private static long getCount(Map<String, Long> byStatus, String key) {
        Long val = byStatus.get(key);
        return val != null ? val : 0L;
    }
}
