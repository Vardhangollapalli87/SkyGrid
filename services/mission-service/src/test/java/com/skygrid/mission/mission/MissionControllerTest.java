package com.skygrid.mission.mission;

import com.skygrid.mission.api.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MissionControllerTest {

    @Mock
    private MissionService missionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MissionController(missionService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createsMissionWithLocationHeader() throws Exception {
        UUID id = UUID.randomUUID();
        when(missionService.createMission(any(MissionRequest.class)))
                .thenReturn(new MissionResponse(id, "Survey", null, Instant.parse("2026-01-01T00:00:00Z")));

        mockMvc.perform(post("/api/v1/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Survey"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Location", "/api/v1/missions/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Survey"))
                .andExpect(jsonPath("$.geometry").doesNotExist());
    }

    @Test
    void rejectsBlankMissionName() throws Exception {
        mockMvc.perform(post("/api/v1/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void rejectsMalformedJsonBody() throws Exception {
        mockMvc.perform(post("/api/v1/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void mapsInvalidPolygonToBadRequest() throws Exception {
        when(missionService.createMission(any(MissionRequest.class)))
                .thenThrow(new InvalidMissionGeometryException("Longitude must be between -180 and 180."));

        mockMvc.perform(post("/api/v1/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Survey","geometry":{"type":"Polygon","coordinates":[]}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid mission geometry"));
    }

    @Test
    void checksMissionAgainstRestrictedZones() throws Exception {
        UUID missionId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        when(missionService.checkRestrictedZones(missionId)).thenReturn(new RestrictedZoneCheckResponse(
                missionId, true, "Mission polygon intersects one or more stored restricted-zone polygons.",
                "Restricted-zone data may be synthetic demonstration data and is not an official aviation source.",
                List.of(new RestrictedZoneCheckResponse.ZoneMatch(zoneId, "Synthetic zone", true))));

        mockMvc.perform(get("/api/v1/missions/{id}/restricted-zone-check", missionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value(missionId.toString()))
                .andExpect(jsonPath("$.intersects").value(true))
                .andExpect(jsonPath("$.zones[0].id").value(zoneId.toString()))
                .andExpect(jsonPath("$.zones[0].demonstration").value(true));
    }

    @Test
    void rejectsRestrictedZoneCheckForMissionWithoutGeometry() throws Exception {
        UUID missionId = UUID.randomUUID();
        when(missionService.checkRestrictedZones(missionId)).thenThrow(new MissionGeometryRequiredException());

        mockMvc.perform(get("/api/v1/missions/{id}/restricted-zone-check", missionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Mission geometry required"));
    }

    @Test
    void returnsNotFoundForUnknownMission() throws Exception {
        UUID id = UUID.randomUUID();
        when(missionService.getMission(id)).thenThrow(new MissionNotFoundException(id));

        mockMvc.perform(get("/api/v1/missions/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Mission not found"));
    }

    @Test
    void returnsGeometryWhenPresentInMissionResponse() throws Exception {
        UUID id = UUID.randomUUID();
        com.fasterxml.jackson.databind.JsonNode geometry = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("""
                        {"type":"Polygon","coordinates":[[[77.59,12.97],[77.60,12.97],
                         [77.60,12.98],[77.59,12.98],[77.59,12.97]]]}
                        """);
        when(missionService.getMission(id)).thenReturn(new MissionResponse(
                id, "Survey", null, Instant.parse("2026-01-01T00:00:00Z"), geometry));

        mockMvc.perform(get("/api/v1/missions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geometry.type").value("Polygon"))
                .andExpect(jsonPath("$.geometry.coordinates[0][0][0]").value(77.59));
    }

    @Test
    void updatesMissionRouteWithoutChangingPolygonContract() throws Exception {
        UUID id = UUID.randomUUID();
        com.fasterxml.jackson.databind.JsonNode route = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"type\":\"LineString\",\"coordinates\":[[77.59,12.97],[77.60,12.97]]}");
        when(missionService.updateMission(org.mockito.ArgumentMatchers.eq(id), any(MissionRequest.class)))
                .thenReturn(new MissionResponse(id, "Route", null, Instant.parse("2026-01-01T00:00:00Z"), null, route));

        mockMvc.perform(put("/api/v1/missions/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Route\",\"route\":{\"type\":\"LineString\",\"coordinates\":[[77.59,12.97],[77.60,12.97]]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.type").value("LineString"))
                .andExpect(jsonPath("$.route.coordinates[0][0]").value(77.59))
                .andExpect(jsonPath("$.geometry").doesNotExist());
    }

    @Test
    void listsMissionsWithBoundedPagination() throws Exception {
        when(missionService.listMissions(any(PageRequest.class))).thenReturn(
                new org.springframework.data.domain.PageImpl<>(
                        List.of(new MissionResponse(UUID.randomUUID(), "Survey", null, Instant.now())),
                        PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/missions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        verify(missionService).listMissions(PageRequest.of(0, 20,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));
    }
}
