package com.skygrid.mission.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

final class NeonDatabaseUrl {

    private static final String INVALID_URL_MESSAGE =
            "DATABASE_URL must be a PostgreSQL URL with username, password, host, and database.";

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private NeonDatabaseUrl(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    static NeonDatabaseUrl parse(String databaseUrl) {
        boolean present = databaseUrl != null && !databaseUrl.isBlank();
        String normalized = normalize(databaseUrl);
        String scheme = detectedScheme(normalized);

        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw failure(present, scheme, "URI syntax is invalid");
        }

        String rawUserInfo = uri.getRawUserInfo();
        String rawPath = uri.getRawPath();
        if (!isPostgresScheme(uri.getScheme())) {
            throw failure(present, scheme, "scheme is missing or unsupported");
        }
        if (uri.getHost() == null) {
            throw failure(present, scheme, "host is missing or invalid");
        }
        if (rawUserInfo == null) {
            throw failure(present, scheme, "username and password are missing");
        }
        if (rawPath == null || rawPath.length() < 2 || rawPath.substring(1).isBlank()) {
            throw failure(present, scheme, "database name is missing");
        }
        if (uri.getFragment() != null) {
            throw failure(present, scheme, "URI fragment is not supported");
        }

        int separator = rawUserInfo.indexOf(':');
        if (separator < 1) {
            throw failure(present, scheme, "username is missing");
        }
        if (separator == rawUserInfo.length() - 1) {
            throw failure(present, scheme, "password is missing");
        }

        String username;
        String password;
        try {
            username = decodeComponent(rawUserInfo.substring(0, separator));
            password = decodeComponent(rawUserInfo.substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            throw failure(present, scheme, "credential percent-encoding is invalid");
        }
        if (username.isBlank()) {
            throw failure(present, scheme, "username is blank");
        }
        if (password.isBlank()) {
            throw failure(present, scheme, "password is blank");
        }

        String authority = uri.getRawAuthority().substring(rawUserInfo.length() + 1);
        String query;
        try {
            query = uri.getRawQuery() == null ? "" : "?" + jdbcQuery(uri.getRawQuery());
        } catch (IllegalArgumentException exception) {
            throw failure(present, scheme, "query parameter encoding is invalid");
        }
        String jdbcUrl = "jdbc:postgresql://" + authority + rawPath + query;
        return new NeonDatabaseUrl(jdbcUrl, username, password);
    }

    private static boolean isPostgresScheme(String scheme) {
        return "postgresql".equalsIgnoreCase(scheme) || "postgres".equalsIgnoreCase(scheme);
    }

    private static String normalize(String databaseUrl) {
        if (databaseUrl == null) {
            return "";
        }

        String value = trimUrlBoundaries(databaseUrl);
        String variableName = "DATABASE_URL";
        if (value.regionMatches(true, 0, variableName, 0, variableName.length())) {
            int index = variableName.length();
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            if (index < value.length() && value.charAt(index) == '=') {
                value = trimUrlBoundaries(value.substring(index + 1));
            }
        }

        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                value = trimUrlBoundaries(value.substring(1, value.length() - 1));
            }
        }
        return value;
    }

    private static String trimUrlBoundaries(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isIgnorableBoundaryCharacter(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = value.codePointBefore(end);
            if (!isIgnorableBoundaryCharacter(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private static boolean isIgnorableBoundaryCharacter(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT;
    }

    private static String detectedScheme(String value) {
        int separator = value.indexOf(':');
        if (separator < 1) {
            return "unrecognized";
        }
        String candidate = value.substring(0, separator);
        if (!candidate.matches("[A-Za-z][A-Za-z0-9+.-]{0,31}")) {
            return "unrecognized";
        }
        return candidate;
    }

    private static InvalidDatabaseUrlException failure(boolean present, String scheme, String condition) {
        return new InvalidDatabaseUrlException(INVALID_URL_MESSAGE, present, scheme, condition);
    }

    String jdbcUrl() {
        return jdbcUrl;
    }

    String username() {
        return username;
    }

    String password() {
        return password;
    }

    private static String decodeComponent(String value) {
        // URLDecoder normally treats '+' as a space; in URI user-info '+' is literal unless escaped.
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String jdbcQuery(String rawQuery) {
        String[] parameters = rawQuery.split("&", -1);
        for (int index = 0; index < parameters.length; index++) {
            int separator = parameters[index].indexOf('=');
            String rawName = separator < 0 ? parameters[index] : parameters[index].substring(0, separator);
            if ("channel_binding".equalsIgnoreCase(URLDecoder.decode(rawName, StandardCharsets.UTF_8))) {
                parameters[index] = "channelBinding" + (separator < 0 ? "" : parameters[index].substring(separator));
            }
        }
        return String.join("&", parameters);
    }

    static final class InvalidDatabaseUrlException extends IllegalArgumentException {

        private final boolean present;
        private final String scheme;
        private final String failedCondition;

        private InvalidDatabaseUrlException(String message, boolean present, String scheme, String failedCondition) {
            super(message);
            this.present = present;
            this.scheme = scheme;
            this.failedCondition = failedCondition;
        }

        boolean present() {
            return present;
        }

        String scheme() {
            return scheme;
        }

        String failedCondition() {
            return failedCondition;
        }
    }

    @Override
    public String toString() {
        return "NeonDatabaseUrl{jdbcUrl='" + jdbcUrl + "', username='[REDACTED]', password='[REDACTED]'}";
    }
}
