package com.skygrid.mission.config;

final class DatabaseUrlDiagnostics {

    private DatabaseUrlDiagnostics() {
    }

    static Summary inspect(String value) {
        boolean present = value != null && !value.isBlank();
        int length = value == null ? 0 : value.length();
        String firstCodePoint = value == null || value.isEmpty()
                ? "none"
                : String.format("U+%04X", value.codePointAt(0));
        boolean containsSchemeDelimiter = value != null && value.contains("://");
        String detectedScheme = detectScheme(value);
        return new Summary(present, length, firstCodePoint, containsSchemeDelimiter, detectedScheme);
    }

    private static String detectScheme(String value) {
        if (value == null) {
            return "unrecognized";
        }
        String candidateInput = value.stripLeading();
        int separator = candidateInput.indexOf(':');
        if (separator < 1) {
            return "unrecognized";
        }
        String candidate = candidateInput.substring(0, separator);
        if (!candidate.matches("[A-Za-z][A-Za-z0-9+.-]{0,31}")) {
            return "unrecognized";
        }
        return candidate;
    }

    record Summary(boolean present, int length, String firstCodePoint,
                   boolean containsSchemeDelimiter, String detectedScheme) {
    }
}
