package org.workfitai.jobservice.util;

import java.util.regex.Pattern;

public class SanitizeInputs {

    private static final Pattern SANITIZE_REGEX = Pattern.compile("[\n\r\t]");
    private static final Pattern ALPHANUMERIC_REGEX = Pattern.compile("[a-zA-Z0-9]*");
    private static final Pattern ALPHANUMERIC_AND_SPACES_REGEX = Pattern.compile("[\\w\\s]*");

    private SanitizeInputs() {
        throw new IllegalStateException("Utility class: SanitizeInputs");
    }

    public static String sanitizeInput(String inputString) {
        if (inputString == null) return null;
        return SANITIZE_REGEX.matcher(inputString).replaceAll("_");
    }

    public static boolean isAlphaNumeric(String inputString) {
        if (inputString == null) return false;
        return ALPHANUMERIC_REGEX.matcher(inputString).matches();
    }

    public static boolean isLettersNumbersAndSpaces(String inputString) {
        if (inputString == null) return false;
        return ALPHANUMERIC_AND_SPACES_REGEX.matcher(inputString).matches();
    }
}
