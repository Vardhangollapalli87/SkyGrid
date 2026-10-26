package com.skygrid.mission.mission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private RestrictedZoneRepository restrictedZoneRepository;

    @Mock
    private AirspaceFeatureRepository airspaceFeatureRepository;

    @Mock
    private MissionRouteSpatialRepository routeSpatialRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MissionService missionService;

    @Test
    void createsMissionAndReturnsResponseDto() {
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionResponse response = missionService.createMission(new MissionRequest("  Survey  ", "Demo"));

        assertNotNull(response.id());
        assertEquals("Survey", response.name());
        assertEquals("Demo", response.description());
        assertNotNull(response.createdAt());
        verify(missionRepository).save(any(Mission.class));
    }

    @Test
    void listsMissionsAsResponseDtos() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Mission mission = new Mission(UUID.randomUUID(), "Survey", null, createdAt);
        when(missionRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mission), PageRequest.of(0, 20), 1));

        Page<MissionResponse> responses = missionService.listMissions(PageRequest.of(0, 20));

        assertEquals(1, responses.getTotalElements());
        assertEquals(mission.getId(), responses.getContent().getFirst().id());
        assertEquals(createdAt, responses.getContent().getFirst().createdAt());
    }

    @Test
    void returnsMissionWhenFound() {
        UUID id = UUID.randomUUID();
        Mission mission = new Mission(id, "Survey", null, Instant.now());
        when(missionRepository.findById(id)).thenReturn(Optional.of(mission));

        assertEquals(id, missionService.getMission(id).id());
    }

    @Test
    void throwsNotFoundWhenMissionDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(missionRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(MissionNotFoundException.class, () -> missionService.getMission(id));
    }

    @Test
    void createsAndUpdatesPersistedLineStringRoute() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var route = mapper.readTree("{\"type\":\"LineString\",\"coordinates\":[[77.59,12.97],[77.60,12.97]]}");
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MissionResponse created = missionService.createMission(new MissionRequest("Route", null, null, route));
        assertEquals("LineString", created.route().path("type").asText());
        when(missionRepository.findById(created.id())).thenAnswer(invocation -> Optional.of(missionRepository.save(
                new Mission(created.id(), "Route", null, Instant.now(), null, MissionGeometryCodec.parseRoute(route)))));

        MissionResponse updated = missionService.updateMission(created.id(), new MissionRequest("Edited route", null, null, route));
        assertEquals("Edited route", updated.name());
        assertEquals(77.60, updated.route().path("coordinates").path(1).path(0).asDouble());
    }

    @Test
    void createsAndListsMultipleRoutesWithoutChangingAreaGeometry() throws Exception {
        UUID areaId = UUID.randomUUID();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var polygonNode = mapper.readTree("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[2,0],[2,2],[0,2],[0,0]]]}" );
        var routeNode = mapper.readTree("{\"type\":\"LineString\",\"coordinates\":[[0.2,0.2],[1,1]]}");
        Mission area = new Mission(areaId, "Area", null, Instant.now(), MissionGeometryCodec.parse(polygonNode));
        when(missionRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MissionResponse first = missionService.createAssociatedRoute(areaId, new MissionRequest("Route A", null, null, routeNode));
        MissionResponse second = missionService.createAssociatedRoute(areaId, new MissionRequest("Route B", null, null, routeNode));
        assertEquals(areaId, first.associatedMissionId());
        assertEquals("Polygon", MissionGeometryCodec.toGeoJson(area.getGeometry()).path("type").asText());
        Mission firstRoute = new Mission(first.id(), first.name(), null, first.createdAt(), null, MissionGeometryCodec.parseRoute(routeNode));
        Mission secondRoute = new Mission(second.id(), second.name(), null, second.createdAt(), null, MissionGeometryCodec.parseRoute(routeNode));
        firstRoute.associateWith(area); secondRoute.associateWith(area);
        when(missionRepository.findByAssociatedMission_IdOrderByCreatedAtDesc(areaId)).thenReturn(List.of(firstRoute, secondRoute));

        assertEquals(2, missionService.listAssociatedRoutes(areaId).size());
        assertEquals("Polygon", MissionGeometryCodec.toGeoJson(area.getGeometry()).path("type").asText());
    }

    @Test
    void restrictedZoneCheckRequiresMissionGeometry() {
        UUID id = UUID.randomUUID();
        when(missionRepository.findById(id))
                .thenReturn(Optional.of(new Mission(id, "Survey", null, Instant.now())));

        assertThrows(MissionGeometryRequiredException.class, () -> missionService.checkRestrictedZones(id));
    }

    @Test
    void restrictedZoneCheckReturnsSpatialMatches() throws Exception {
        UUID id = UUID.randomUUID();
        Mission mission = new Mission(id, "Survey", null, Instant.now(),
                MissionGeometryCodec.parse(new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                        {"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}
                        """)));
        RestrictedZoneRepository.RestrictedZoneIntersection match =
                org.mockito.Mockito.mock(RestrictedZoneRepository.RestrictedZoneIntersection.class);
        UUID zoneId = UUID.randomUUID();
        when(match.getId()).thenReturn(zoneId);
        when(match.getName()).thenReturn("Demo zone");
        when(match.getDemonstration()).thenReturn(true);
        when(missionRepository.findById(id)).thenReturn(Optional.of(mission));
        when(restrictedZoneRepository.findIntersectionsForMission(id)).thenReturn(List.of(match));

        RestrictedZoneCheckResponse response = missionService.checkRestrictedZones(id);

        assertEquals(true, response.intersects());
        assertEquals(zoneId, response.zones().getFirst().id());
        assertEquals("Demo zone", response.zones().getFirst().name());
    }
}
