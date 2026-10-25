package com.skygrid.mission.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NeonDatabaseUrlTest {

    @Test
    void convertsPostgresqlUrlAndPreservesSslChannelBindingAndOtherQueryParameters() {
        String url = "postgresql://%24%7BDB_USER%7D:%24%7BDB_PASSWORD%7D@db.example.invalid/skygrid"
                + "?sslmode=require&channel_binding=require&application_name=skygrid";

        NeonDatabaseUrl parsed = NeonDatabaseUrl.parse(url);

        assertEquals("jdbc:postgresql://db.example.invalid/skygrid"
                        + "?sslmode=require&channelBinding=require&application_name=skygrid",
                parsed.jdbcUrl());
        assertEquals("${DB_USER}", parsed.username());
        assertEquals("${DB_PASSWORD}", parsed.password());
        assertFalse(parsed.toString().contains("DB_PASSWORD"));
    }

    @Test
    void acceptsPostgresSchemeAndDecodesReservedCredentialCharacters() {
        String url = "postgres://%24%7BDB_USER%2BNAME%7D:%24%7BDB_PASSWORD%3A42%7D"
                + "@db.example.invalid/skygrid?sslmode=require";

        NeonDatabaseUrl parsed = NeonDatabaseUrl.parse(url);

        assertEquals("jdbc:postgresql://db.example.invalid/skygrid?sslmode=require", parsed.jdbcUrl());
        assertEquals("${DB_USER+NAME}", parsed.username());
        assertEquals("${DB_PASSWORD:42}", parsed.password());
        assertFalse(parsed.toString().contains("DB_PASSWORD"));
    }

    @Test
    void acceptsWhitespaceAndQuotedEnvironmentAssignmentWithoutLoggingCredentialValues() {
        String input = " DATABASE_URL= 'postgresql://%24%7BDB_USER%7D:%24%7BDB_PASSWORD%7D"
                + "@db.example.invalid/skygrid?sslmode=require&channel_binding=require' ";

        NeonDatabaseUrl parsed = NeonDatabaseUrl.parse(input);

        assertEquals("jdbc:postgresql://db.example.invalid/skygrid"
                        + "?sslmode=require&channelBinding=require",
                parsed.jdbcUrl());
        assertEquals("${DB_USER}", parsed.username());
        assertEquals("${DB_PASSWORD}", parsed.password());
        assertFalse(parsed.toString().contains("DB_PASSWORD"));
    }

    @Test
    void acceptsNeonPoolerUrlShapeWithUnderscoreCredentialsAndSslParameters() {
        String url = "postgresql://demo_owner:synthetic_password@ep-example-pooler.c-8.us-east-1.aws.neon.tech"
                + "/neondb?sslmode=require&channel_binding=require";

        NeonDatabaseUrl parsed = NeonDatabaseUrl.parse(url);

        assertEquals("jdbc:postgresql://ep-example-pooler.c-8.us-east-1.aws.neon.tech/neondb"
                        + "?sslmode=require&channelBinding=require",
                parsed.jdbcUrl());
        assertEquals("demo_owner", parsed.username());
        assertEquals("synthetic_password", parsed.password());
    }

    @Test
    void removesByteOrderMarkAndZeroWidthClipboardCharactersAroundUrl() {
        String url = "\uFEFF\u200B\u00A0postgresql://demo_owner:synthetic_password"
                + "@db.example.invalid/skygrid?sslmode=require\uFEFF\u00A0";

        NeonDatabaseUrl parsed = NeonDatabaseUrl.parse(url);

        assertEquals("jdbc:postgresql://db.example.invalid/skygrid?sslmode=require", parsed.jdbcUrl());
        assertEquals("demo_owner", parsed.username());
        assertEquals("synthetic_password", parsed.password());
    }

    @Test
    void diagnosticsExposeOnlySafeShapeMetadata() {
        String value = "postgresql://synthetic_user:synthetic_password@private.example.invalid/database";

        DatabaseUrlDiagnostics.Summary diagnostics = DatabaseUrlDiagnostics.inspect(value);

        assertEquals(true, diagnostics.present());
        assertEquals(value.length(), diagnostics.length());
        assertEquals("U+0070", diagnostics.firstCodePoint());
        assertEquals(true, diagnostics.containsSchemeDelimiter());
        assertEquals("postgresql", diagnostics.detectedScheme());
        assertFalse(diagnostics.toString().contains("synthetic_user"));
        assertFalse(diagnostics.toString().contains("synthetic_password"));
        assertFalse(diagnostics.toString().contains("private.example.invalid"));
    }

    @Test
    void diagnosticsIdentifyUnexpectedLeadingCodePointWithoutEchoingIt() {
        String value = "\uFEFFpostgresql://synthetic_user:synthetic_password@db.example.invalid/database";

        DatabaseUrlDiagnostics.Summary diagnostics = DatabaseUrlDiagnostics.inspect(value);

        assertEquals("U+FEFF", diagnostics.firstCodePoint());
        assertEquals(true, diagnostics.containsSchemeDelimiter());
        assertEquals("unrecognized", diagnostics.detectedScheme());
        assertFalse(diagnostics.toString().contains("synthetic_user"));
    }

    @Test
    void rejectsMissingRequiredFieldsAndMalformedPowerShellPromptInputWithoutEchoingInput() {
        String username = "%24%7BDB_USER%7D";
        String password = "%24%7BDB_PASSWORD%7D";
        String host = "db.example.invalid";
        String database = "skygrid";
        String[] invalidUrls = {
                "postgresql://" + username + ":" + password + "@" + host + "/",
                "postgresql://:" + password + "@" + host + "/" + database,
                "postgresql://" + username + ":@" + host + "/" + database,
                "'postgresql://" + username + ":" + password + "@" + host + "/" + database,
                "postgresql://" + username + ":" + password + "@/" + database,
        };

        for (String invalidUrl : invalidUrls) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> NeonDatabaseUrl.parse(invalidUrl));
            assertEquals("DATABASE_URL must be a PostgreSQL URL with username, password, host, and database.",
                    exception.getMessage());
            assertFalse(exception.getMessage().contains("DB_PASSWORD"));
        }
    }

    @Test
    void reportsSafeUriSyntaxDiagnosticsWithoutIncludingInput() {
        String malformedUrl = "postgresql://%24%7BDB_USER%7D:%24%7BDB_PASSWORD%7D@db.example.invalid/skygrid path";

        NeonDatabaseUrl.InvalidDatabaseUrlException exception = assertThrows(
                NeonDatabaseUrl.InvalidDatabaseUrlException.class,
                () -> NeonDatabaseUrl.parse(malformedUrl));

        assertEquals(true, exception.present());
        assertEquals("postgresql", exception.scheme());
        assertEquals("URI syntax is invalid", exception.failedCondition());
        assertFalse(exception.getMessage().contains("DB_PASSWORD"));
    }
}
