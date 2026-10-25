package com.skygrid.mission.mission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private AirspaceFeatureRepository airspaceFeatureRepository;

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

    @Test
    @Transactional
    void importedRestrictionIntersectionsExcludeGuidanceAndSeparatePolygons() {
        UUID overlappingMissionId = UUID.randomUUID();
        UUID separateMissionId = UUID.randomUUID();
        missionRepository.saveAndFlush(new Mission(overlappingMissionId, "Airspace overlap query", null,
                Instant.now(), polygon(0, 0, 1, 1)));
        missionRepository.saveAndFlush(new Mission(separateMissionId, "Airspace separate query", null,
                Instant.now(), polygon(20, 20, 21, 21)));

        jdbcTemplate.update("""
                insert into airspace_dataset_imports(country_code,source_name,dataset_name,category,status,input_count,
                  spatial_count,unconverted_count,rejected_count,duplicate_count,imported_at,last_attempt_at,
                  coverage_verified,limitations)
                values ('USA','test-source','test-dataset','MIXED','SUCCESS',3,3,0,0,0,current_timestamp,
                  current_timestamp,false,'integration fixture')
                """);
        UUID overlapId = UUID.randomUUID();
        insertFeature(overlapId, "overlap", "RESTRICTION", "POLYGON ((0.5 0.5, 2 0.5, 2 2, 0.5 2, 0.5 0.5))");
        insertFeature(UUID.randomUUID(), "separate", "RESTRICTION", "POLYGON ((10 10, 12 10, 12 12, 10 12, 10 10))");
        insertFeature(UUID.randomUUID(), "guidance", "AVIATION_INFORMATION", "POLYGON ((0.5 0.5, 2 0.5, 2 2, 0.5 2, 0.5 0.5))");

        var overlap = airspaceFeatureRepository.findRestrictionIntersections(overlappingMissionId);
        var separate = airspaceFeatureRepository.findRestrictionIntersections(separateMissionId);

        assertEquals(1, overlap.size());
        assertEquals(overlapId, overlap.getFirst().getId());
        assertTrue(separate.isEmpty());
    }

    private void insertFeature(UUID id, String sourceId, String category, String wkt) {
        jdbcTemplate.update("""
                insert into airspace_features(id,source_id,source_name,country_code,dataset_name,name,category,
                  classification,geometry,source_properties,geometry_status,imported_at,last_attempt_at)
                values (?,?,?,'USA','test-dataset',?,?,?,ST_SetSRID(ST_GeomFromText(?),4326),'{}'::jsonb,
                  'VALID',current_timestamp,current_timestamp)
                """, id, sourceId, "test-source", sourceId, category,
                "RESTRICTION".equals(category) ? "PROHIBITED" : "GUIDANCE", wkt);
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
