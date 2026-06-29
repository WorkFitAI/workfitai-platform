package org.workfitai.jobservice.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SanitizeInputsTest {

    @Test
    void sanitizeInput_returnsNull_whenInputIsNull() {
        assertThat(SanitizeInputs.sanitizeInput(null)).isNull();
    }

    @Test
    void sanitizeInput_replacesNewlinesTabsAndCarriageReturns() {
        assertThat(SanitizeInputs.sanitizeInput("line1\nline2\ttab\rcr")).isEqualTo("line1_line2_tab_cr");
    }

    @Test
    void isAlphaNumeric_returnsFalse_whenInputIsNull() {
        assertThat(SanitizeInputs.isAlphaNumeric(null)).isFalse();
    }

    @Test
    void isAlphaNumeric_returnsTrue_forAlphanumericString() {
        assertThat(SanitizeInputs.isAlphaNumeric("Abc123")).isTrue();
    }

    @Test
    void isAlphaNumeric_returnsFalse_whenContainsSpacesOrSymbols() {
        assertThat(SanitizeInputs.isAlphaNumeric("Abc 123!")).isFalse();
    }

    @Test
    void isLettersNumbersAndSpaces_returnsFalse_whenInputIsNull() {
        assertThat(SanitizeInputs.isLettersNumbersAndSpaces(null)).isFalse();
    }

    @Test
    void isLettersNumbersAndSpaces_returnsTrue_forLettersNumbersAndSpaces() {
        assertThat(SanitizeInputs.isLettersNumbersAndSpaces("Hanoi 2024")).isTrue();
    }

    @Test
    void isLettersNumbersAndSpaces_returnsFalse_whenContainsSymbols() {
        assertThat(SanitizeInputs.isLettersNumbersAndSpaces("Hanoi!#@")).isFalse();
    }
}
