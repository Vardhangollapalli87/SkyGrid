package com.skygrid.mission.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end checks against Neon; test data is rolled back with the test transaction. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "debug=false"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "SKYGRID_POSTGIS_INTEGRATION", matches = "true")
class MissionSpatialApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestrictedZoneRepository restrictedZoneRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsRetrievesAndChecksGeoJsonMissionThroughApi() throws Exception {
        UUID zoneId = UUID.randomUUID();
        restrictedZoneRepository.saveAndFlush(new RestrictedZone(zoneId, "Synthetic integration zone",
                polygon(0.5, 0.5, 2, 2), true));

        var createResult = mockMvc.perform(post("/api/v1/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Integration polygon",
                                  "description": "Synthetic test transaction",
                                  "geometry": {
                                    "type": "Polygon",
                                    "coordinates": [[[0,0],[1,0],[1,1],[0,1],[0,0]]]
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.geometry.type").value("Polygon"))
                .andReturn();

        UUID missionId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("id").asText());

        mockMvc.perform(get("/api/v1/missions/{id}", missionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geometry.coordinates[0][0]").isArray());

        mockMvc.perform(get("/api/v1/missions/{id}/restricted-zone-check", missionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value(missionId.toString()))
                .andExpect(jsonPath("$.intersects").value(true))
                .andExpect(jsonPath("$.zones[0].id").value(zoneId.toString()))
                .andExpect(jsonPath("$.zones[0].demonstration").value(true));
    }

    @Test
    void missionWithoutGeometryCanStillBeCreatedButCannotBeSpatiallyChecked() throws Exception {
        var result = mockMvc.perform(post("/api/v1/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Legacy-compatible mission"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID missionId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("id").asText());

        mockMvc.perform(get("/api/v1/missions/{id}/restricted-zone-check", missionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Mission geometry required"));
    }

    @Test
    void airspaceInventoryApiMatchesCommittedDatabaseRows() throws Exception {
        var databaseRows = jdbcTemplate.query("""
                select d.country_code,d.dataset_name,d.status,d.input_count,d.spatial_count,d.unconverted_count,
                       d.rejected_count,d.duplicate_count,d.inserted_count,d.updated_count,d.skipped_count,
                       d.non_spatial_count,d.failed_count,count(f.id) as stored_count,
                       count(f.id) filter (where f.geometry is not null and f.geometry_status='VALID') as stored_spatial,
                       count(f.id) filter (where f.geometry is null and f.geometry_status<>'INVALID_GEOMETRY') as stored_non_spatial,
                       count(f.id) filter (where f.geometry_status='INVALID_GEOMETRY') as stored_invalid_geometry
                  from airspace_dataset_imports d left join airspace_features f
                    on f.country_code=d.country_code and f.source_name=d.source_name and f.dataset_name=d.dataset_name
                 group by d.country_code,d.dataset_name,d.status,d.input_count,d.spatial_count,d.unconverted_count,
                          d.rejected_count,d.duplicate_count,d.inserted_count,d.updated_count,d.skipped_count,
                          d.non_spatial_count,d.failed_count
                """, (rs, n) -> new Object[]{rs.getString("country_code"),rs.getString("dataset_name"),rs.getString("status"),
                rs.getInt("input_count"),rs.getInt("spatial_count"),rs.getInt("non_spatial_count"),rs.getInt("rejected_count"),
                rs.getInt("duplicate_count"),rs.getInt("inserted_count"),rs.getInt("updated_count"),rs.getInt("skipped_count"),
                rs.getInt("non_spatial_count"),rs.getInt("failed_count"),rs.getLong("stored_count"),rs.getLong("stored_spatial"),
                rs.getLong("stored_non_spatial"),rs.getLong("stored_invalid_geometry")});
        JsonNode api = objectMapper.readTree(mockMvc.perform(get("/api/v1/airspace/datasets"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(databaseRows.size(), api.size());
        for (Object[] db : databaseRows) {
            JsonNode row = java.util.stream.StreamSupport.stream(api.spliterator(), false)
                    .filter(item -> item.path("country").asText().equals(db[0])
                            && item.path("dataset").asText().equals(db[1])).findFirst().orElseThrow();
            assertEquals(db[2], row.path("status").asText());
            assertEquals(db[3], row.path("inputCount").asInt());
            assertEquals(db[4], row.path("spatialFeatures").asInt());
            assertEquals(db[5], row.path("unconvertedRecords").asInt());
            assertEquals(db[6], row.path("rejectedCount").asInt());
            assertEquals(db[7], row.path("duplicateCount").asInt());
            assertEquals(db[8], row.path("insertedCount").asInt());
            assertEquals(db[9], row.path("updatedCount").asInt());
            assertEquals(db[10], row.path("skippedCount").asInt());
            assertEquals(db[11], row.path("nonSpatialCount").asInt());
            assertEquals(db[12], row.path("failedCount").asInt());
            assertEquals(db[11], row.path("nonSpatialCount").asInt());
            if ("SUCCESS".equals(db[2]) || "PARTIAL".equals(db[2])) {
                assertEquals((int) db[3] - (int) db[7], ((Number) db[13]).intValue());
                assertEquals(((Number) db[4]).intValue(), ((Number) db[14]).intValue());
                assertEquals(((Number) db[11]).intValue(), ((Number) db[15]).intValue());
                assertEquals((int) db[3] - ((Number) db[13]).intValue() - (int) db[7]
                        + ((Number) db[16]).intValue(), (int) db[6]);
            }
        }
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
