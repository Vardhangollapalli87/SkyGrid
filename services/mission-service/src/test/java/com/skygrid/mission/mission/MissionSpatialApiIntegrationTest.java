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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
