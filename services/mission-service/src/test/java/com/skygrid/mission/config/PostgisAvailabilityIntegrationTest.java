package com.skygrid.mission.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest(properties = "debug=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "SKYGRID_POSTGIS_INTEGRATION", matches = "true")
class PostgisAvailabilityIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void configuredDatabaseHasPostgisAndExecutesSridAndIntersectionQueries() {
        String extensionVersion = jdbcTemplate.queryForObject(
                "select extversion from pg_extension where extname = 'postgis'", String.class);
        assertNotNull(extensionVersion, "PostGIS extension must be enabled in the configured database");

        Integer srid = jdbcTemplate.queryForObject(
                "select ST_SRID(ST_GeomFromText('POINT(0.5 0.5)', 4326))", Integer.class);
        assertEquals(4326, srid);

        Boolean intersects = jdbcTemplate.queryForObject("""
                select ST_Intersects(
                    ST_GeomFromText('POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))', 4326),
                    ST_GeomFromText('POINT(0.5 0.5)', 4326))
                """, Boolean.class);
        assertTrue(Boolean.TRUE.equals(intersects));
    }

    @Test
    void existingMissionTableStillHasItsOriginalColumns() {
        var columns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = current_schema() and table_name = 'missions'
                """, String.class);

        assertTrue(columns.containsAll(java.util.List.of("id", "name", "description", "created_at")),
                "Existing mission columns must remain present");
    }
}
