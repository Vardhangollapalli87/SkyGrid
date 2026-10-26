package com.skygrid.mission.mission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opt-in PostGIS checks. Requires the additive route-association schema to exist; writes roll back per test. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "debug=false"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "SKYGRID_POSTGIS_INTEGRATION", matches = "true")
class MissionRouteContainmentIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void checksFullLineInsideAndBoundaryAsContained() throws Exception {
        UUID area = area("[[0,0],[10,0],[10,10],[0,10],[0,0]]");
        UUID inside = route(area, "[[1,1],[9,9]]");
        UUID boundary = route(area, "[[0,0],[10,0]]");
        mockMvc.perform(get("/api/v1/missions/{route}", inside))
                .andExpect(status().isOk()).andExpect(jsonPath("$.route.coordinates[0][0]").value(1.0))
                .andExpect(jsonPath("$.route.coordinates[0][1]").value(1.0));
        check(area, inside).andExpect(jsonPath("$.status").value("WITHIN_OR_ON_BOUNDARY"));
        check(area, boundary).andExpect(jsonPath("$.status").value("WITHIN_OR_ON_BOUNDARY"));
    }

    @Test
    void detectsAnOutsideSegmentWhenBothEndpointsAreInsideConcavePolygon() throws Exception {
        UUID area = area("[[0,0],[2,0],[2,2],[1.5,2],[1.5,0.5],[0.5,0.5],[0.5,2],[0,2],[0,0]]");
        UUID route = route(area, "[[0.2,1.5],[1.8,1.5]]");
        check(area, route).andExpect(jsonPath("$.status").value("PARTLY_OUTSIDE"))
                .andExpect(jsonPath("$.outsideGeometry.type").value("MultiLineString"));
    }

    @Test
    void distinguishesFullyOutsideRoute() throws Exception {
        UUID area = area("[[0,0],[1,0],[1,1],[0,1],[0,0]]");
        UUID route = route(area, "[[2,2],[3,3]]");
        check(area, route).andExpect(jsonPath("$.status").value("OUTSIDE"));
    }

    @Test
    void allowsMultipleRoutesAndRouteUpdatesDoNotAlterMissionPolygon() throws Exception {
        UUID area = area("[[0,0],[2,0],[2,2],[0,2],[0,0]]");
        UUID first = route(area, "[[0.2,0.2],[1,1]]");
        UUID second = route(area, "[[0.3,0.3],[1.2,1.2]]");
        String polygonBefore = jdbc.queryForObject("select ST_AsText(geometry) from missions where id=?", String.class, area);
        mockMvc.perform(put("/api/v1/missions/{area}/routes/{route}", area, first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated route\",\"route\":{\"type\":\"LineString\",\"coordinates\":[[0.4,0.4],[1.4,1.4]]}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.associatedMissionId").value(area.toString()));
        assertEquals(polygonBefore, jdbc.queryForObject("select ST_AsText(geometry) from missions where id=?", String.class, area));
        mockMvc.perform(get("/api/v1/missions/{area}/routes", area))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
        assertEquals(2, jdbc.queryForObject("select count(*) from missions where associated_mission_id=?", Integer.class, area));
        mockMvc.perform(get("/api/v1/missions/{id}", second)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/missions/{area}", area).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Edited area\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[3,0],[3,3],[0,3],[0,0]]]}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.geometry.type").value("Polygon"));
        assertEquals(2, jdbc.queryForObject("select count(*) from missions where associated_mission_id=?", Integer.class, area));
    }

    @Test
    void keepsOldStandaloneRouteRowsUnassociatedUntilExplicitlyLinked() throws Exception {
        UUID area = area("[[0,0],[2,0],[2,2],[0,2],[0,0]]");
        var created = mockMvc.perform(post("/api/v1/missions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Standalone\",\"route\":{\"type\":\"LineString\",\"coordinates\":[[0.2,0.2],[1,1]]}}"))
                .andExpect(status().isCreated()).andReturn();
        UUID route = UUID.fromString(mapper.readTree(created.getResponse().getContentAsString()).path("id").asText());
        assertEquals(null, jdbc.queryForObject("select associated_mission_id from missions where id=?", UUID.class, route));
        mockMvc.perform(put("/api/v1/routes/{route}/association/{area}", route, area)).andExpect(status().isOk());
        assertEquals(area, jdbc.queryForObject("select associated_mission_id from missions where id=?", UUID.class, route));
    }

    private UUID area(String ring) throws Exception {
        String body = "{\"name\":\"Integration area\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[" + ring + "]}}";
        var result = mockMvc.perform(post("/api/v1/missions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    private UUID route(UUID area, String points) throws Exception {
        String body = "{\"name\":\"Integration route\",\"route\":{\"type\":\"LineString\",\"coordinates\":" + points + "}}";
        var result = mockMvc.perform(post("/api/v1/missions/{area}/routes", area).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions check(UUID area, UUID route) throws Exception {
        return mockMvc.perform(get("/api/v1/missions/{area}/routes/{route}/containment", area, route))
                .andExpect(status().isOk()).andExpect(jsonPath("$.missionId").value(area.toString()))
                .andExpect(jsonPath("$.routeId").value(route.toString()));
    }
}
