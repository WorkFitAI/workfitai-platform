package org.workfitai.applicationservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ApplicationFunnelCalculatorTest {

    @Test
    void calculateFunnelRates_happyPath_computesRatesCorrectly() {
        Map<String, Long> byStatus = Map.of(
                "APPLIED", 100L, "REVIEWING", 60L, "INTERVIEW", 30L,
                "OFFER", 10L, "HIRED", 5L);

        Map<String, Double> rates = ApplicationFunnelCalculator.calculateFunnelRates(byStatus);

        assertThat(rates.get("APPLIED_TO_REVIEWING")).isEqualTo(60.0 / 100);
        assertThat(rates.get("REVIEWING_TO_INTERVIEW")).isEqualTo(30.0 / 60);
        assertThat(rates.get("INTERVIEW_TO_OFFER")).isEqualTo(10.0 / 30);
        assertThat(rates.get("OFFER_TO_HIRED")).isEqualTo(5.0 / 10);
    }

    @Test
    void calculateFunnelRates_zeroCounts_allRatesAreZero() {
        Map<String, Long> byStatus = Map.of();

        Map<String, Double> rates = ApplicationFunnelCalculator.calculateFunnelRates(byStatus);

        assertThat(rates.get("APPLIED_TO_REVIEWING")).isEqualTo(0.0);
        assertThat(rates.get("REVIEWING_TO_INTERVIEW")).isEqualTo(0.0);
        assertThat(rates.get("INTERVIEW_TO_OFFER")).isEqualTo(0.0);
        assertThat(rates.get("OFFER_TO_HIRED")).isEqualTo(0.0);
    }

    @Test
    void calculateFunnelRates_onlyApplied_downstreamRatesAreZero() {
        Map<String, Long> byStatus = Map.of("APPLIED", 50L);

        Map<String, Double> rates = ApplicationFunnelCalculator.calculateFunnelRates(byStatus);

        assertThat(rates.get("APPLIED_TO_REVIEWING")).isEqualTo(0.0);
        assertThat(rates.get("REVIEWING_TO_INTERVIEW")).isEqualTo(0.0);
        assertThat(rates.get("OFFER_TO_HIRED")).isEqualTo(0.0);
    }

    @Test
    void calculateFunnelRates_perfectConversion_allRatesOne() {
        Map<String, Long> byStatus = Map.of(
                "APPLIED", 10L, "REVIEWING", 10L, "INTERVIEW", 10L,
                "OFFER", 10L, "HIRED", 10L);

        Map<String, Double> rates = ApplicationFunnelCalculator.calculateFunnelRates(byStatus);

        assertThat(rates.get("APPLIED_TO_REVIEWING")).isEqualTo(1.0);
        assertThat(rates.get("OFFER_TO_HIRED")).isEqualTo(1.0);
    }
}
