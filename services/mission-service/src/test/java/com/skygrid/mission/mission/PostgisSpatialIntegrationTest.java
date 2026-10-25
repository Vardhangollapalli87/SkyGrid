package com.skygrid.mission.mission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against the configured local PostGIS database only when explicitly enabled. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "debug=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "SKYGRID_POSTGIS_INTEGRATION", matches = "true")
class PostgisSpatialIntegrationTest {

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private RestrictedZoneRepository restrictedZoneRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void existingMissionRowsRemainReadableAndSpatialIndexExists() {
        var page = missionRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 1));
        assertNotNull(page);

        Boolean indexExists = jdbcTemplate.queryForObject("""
                select exists (
                    select 1 from pg_indexes
                    where schemaname = current_schema()
                      and indexname = 'idx_restricted_zones_geometry_gist'
                )
                """, Boolean.class);
        assertTrue(Boolean.TRUE.equals(indexExists));
    }

    @Test
    void findsOnlyZonesWhoseStoredPostgisPolygonsIntersectMission() {
        UUID missionId = UUID.randomUUID();
        Mission mission = new Mission(missionId, "Integration test mission", null, Instant.now(),
                polygon(0, 0, 1, 1));
        missionRepository.saveAndFlush(mission);

        UUID intersectingId = UUID.randomUUID();
        restrictedZoneRepository.saveAndFlush(new RestrictedZone(intersectingId, "Synthetic overlap",
                polygon(0.5, 0.5, 2, 2), true));
        restrictedZoneRepository.saveAndFlush(new RestrictedZone(UUID.randomUUID(), "Synthetic separate",
                polygon(10, 10, 12, 12), true));

        var matches = restrictedZoneRepository.findIntersectionsForMission(missionId);

        assertEquals(1, matches.size());
        assertEquals(intersectingId, matches.getFirst().getId());
        assertEquals("Synthetic overlap", matches.getFirst().getName());
    }

    private static org.locationtech.jts.geom.Polygon polygon(double minX, double minY, double maxX, double maxY) {
        var factory = new org.locationtech.jts.geom.GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(), 4326);
        var shell = factory.createLinearRing(new org.locationtech.jts.geom.Coordinate[]{
                new org.locationtech.jts.geom.Coordinate(minX, minY),
                new org.locationtech.jts.geom.Coordinate(maxX, minY),
                new org.locationtech.jts.geom.Coordinate(maxX, maxY),
                new org.locationtech.jts.geom.Coordinate(minX, maxY),
                new org.locationtech.jts.geom.Coordinate(minX, minY)
        });
        return factory.createPolygon(shell);
    }
}
