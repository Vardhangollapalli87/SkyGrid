package com.skygrid.mission.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteContainmentServiceTest {
    @Mock MissionRepository missionRepository;
    @Mock RestrictedZoneRepository restrictedZoneRepository;
    @Mock AirspaceFeatureRepository airspaceFeatureRepository;
    @Mock MissionRouteSpatialRepository routeSpatialRepository;

    private MissionService service;
    private UUID missionId;
    private UUID routeId;

    @BeforeEach
    void setUp() {
        service = new MissionService(missionRepository, restrictedZoneRepository, airspaceFeatureRepository,
                routeSpatialRepository, new ObjectMapper());
        missionId = UUID.randomUUID();
        routeId = UUID.randomUUID();
    }

    @Test
    void returnsContainedStatusForWholeLineAndUsesPostgisBoundarySemantics() {
        prepare(squareArea(), line("[[0.2,0.2],[0.8,0.8]]"));
        when(routeSpatialRepository.checkContainment(missionId, routeId)).thenReturn(new MissionRouteSpatialRepository.SpatialResult(true, true, "{\"type\":\"MultiLineString\",\"coordinates\":[]}", "a", "b"));

        RouteContainmentResponse response = service.checkRouteContainment(missionId, routeId);

        assertEquals(RouteContainmentResponse.Status.WITHIN_OR_ON_BOUNDARY, response.status());
        assertEquals("Boundary-touching route segments count as contained (PostGIS ST_Covers).", response.boundarySemantics());
        assertNotNull(response.missionGeometryHash());
    }

    @Test
    void returnsBoundaryRouteAsContained() {
        prepare(squareArea(), line("[[0,0],[1,0]]"));
        when(routeSpatialRepository.checkContainment(missionId, routeId)).thenReturn(new MissionRouteSpatialRepository.SpatialResult(true, true, "{\"type\":\"MultiLineString\",\"coordinates\":[]}", "a", "b"));
        assertEquals(RouteContainmentResponse.Status.WITHIN_OR_ON_BOUNDARY, service.checkRouteContainment(missionId, routeId).status());
    }

    @Test
    void distinguishesPartlyOutsideEvenWhenBothEndpointsAreInside() {
        prepare(notchedArea(), line("[[0.2,1.5],[1.8,1.5]]"));
        when(routeSpatialRepository.checkContainment(missionId, routeId)).thenReturn(new MissionRouteSpatialRepository.SpatialResult(false, true,
                "{\"type\":\"MultiLineString\",\"coordinates\":[[[1,0.5],[2,0.5]]]}", "a", "b"));
        RouteContainmentResponse response = service.checkRouteContainment(missionId, routeId);
        assertEquals(RouteContainmentResponse.Status.PARTLY_OUTSIDE, response.status());
        assertEquals("MultiLineString", response.outsideGeometry().path("type").asText());
    }

    @Test
    void returnsOutsideStatusForDisjointRoute() {
        prepare(squareArea(), line("[[2,2],[3,3]]"));
        when(routeSpatialRepository.checkContainment(missionId, routeId)).thenReturn(new MissionRouteSpatialRepository.SpatialResult(false, false,
                "{\"type\":\"MultiLineString\",\"coordinates\":[[[2,2],[3,3]]]}", "a", "b"));
        assertEquals(RouteContainmentResponse.Status.OUTSIDE, service.checkRouteContainment(missionId, routeId).status());
    }

    @Test
    void returnsMissingWhenAreaGeometryIsAbsent() {
        Mission area = new Mission(missionId, "area", null, Instant.now());
        Mission route = new Mission(routeId, "route", null, Instant.now(), null, line("[[0,0],[1,1]]")); route.associateWith(area);
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(area));
        when(missionRepository.findById(routeId)).thenReturn(Optional.of(route));
        assertEquals(RouteContainmentResponse.Status.MISSING_GEOMETRY, service.checkRouteContainment(missionId, routeId).status());
    }

    @Test
    void returnsMissingWhenAssociatedRouteHasNoLineString() {
        Mission area = new Mission(missionId, "area", null, Instant.now(), squareArea());
        Mission route = new Mission(routeId, "route", null, Instant.now()); route.associateWith(area);
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(area));
        when(missionRepository.findById(routeId)).thenReturn(Optional.of(route));
        assertEquals(RouteContainmentResponse.Status.MISSING_GEOMETRY, service.checkRouteContainment(missionId, routeId).status());
    }

    @Test
    void rejectsInvalidStoredCoordinatesBeforeSpatialQuery() {
        Mission area = new Mission(missionId, "area", null, Instant.now(), squareArea());
        var factory = new org.locationtech.jts.geom.GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(), 4326);
        var invalidLine = factory.createLineString(new org.locationtech.jts.geom.Coordinate[]{new org.locationtech.jts.geom.Coordinate(439, 0), new org.locationtech.jts.geom.Coordinate(1, 1)});
        Mission route = new Mission(routeId, "route", null, Instant.now(), null, invalidLine); route.associateWith(area);
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(area));
        when(missionRepository.findById(routeId)).thenReturn(Optional.of(route));
        assertEquals(RouteContainmentResponse.Status.INVALID_GEOMETRY, service.checkRouteContainment(missionId, routeId).status());
    }

    @Test
    void rejectsInvalidMissionPolygonTopology() {
        var factory = new org.locationtech.jts.geom.GeometryFactory(new org.locationtech.jts.geom.PrecisionModel(), 4326);
        var ring = factory.createLinearRing(new org.locationtech.jts.geom.Coordinate[]{
                new org.locationtech.jts.geom.Coordinate(0, 0), new org.locationtech.jts.geom.Coordinate(1, 1),
                new org.locationtech.jts.geom.Coordinate(0, 1), new org.locationtech.jts.geom.Coordinate(1, 0),
                new org.locationtech.jts.geom.Coordinate(0, 0)});
        Mission area = new Mission(missionId, "area", null, Instant.now(), factory.createPolygon(ring));
        Mission route = new Mission(routeId, "route", null, Instant.now(), null, line("[[0.2,0.2],[0.8,0.8]]")); route.associateWith(area);
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(area));
        when(missionRepository.findById(routeId)).thenReturn(Optional.of(route));
        assertEquals(RouteContainmentResponse.Status.INVALID_GEOMETRY, service.checkRouteContainment(missionId, routeId).status());
    }

    @Test
    void returnsUnavailableWhenThePostgisQueryFails() {
        prepare(squareArea(), line("[[0.2,0.2],[0.8,0.8]]"));
        when(routeSpatialRepository.checkContainment(missionId, routeId))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db failure"));
        assertEquals(RouteContainmentResponse.Status.UNAVAILABLE, service.checkRouteContainment(missionId, routeId).status());
    }

    private void prepare(org.locationtech.jts.geom.Polygon polygon, org.locationtech.jts.geom.LineString line) {
        Mission area = new Mission(missionId, "area", null, Instant.now(), polygon);
        Mission route = new Mission(routeId, "route", null, Instant.now(), null, line); route.associateWith(area);
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(area));
        when(missionRepository.findById(routeId)).thenReturn(Optional.of(route));
    }

    private static org.locationtech.jts.geom.Polygon squareArea() {
        return (org.locationtech.jts.geom.Polygon) MissionGeometryCodec.parse(new ObjectMapper().valueToTree(new java.util.LinkedHashMap<>(java.util.Map.of(
                "type", "Polygon", "coordinates", java.util.List.of(java.util.List.of(java.util.List.of(0,0), java.util.List.of(1,0), java.util.List.of(1,1), java.util.List.of(0,1), java.util.List.of(0,0)))))));
    }

    private static org.locationtech.jts.geom.Polygon notchedArea() {
        try {
            return MissionGeometryCodec.parse(new ObjectMapper().readTree("""
                    {"type":"Polygon","coordinates":[[[0,0],[2,0],[2,2],[1.5,2],[1.5,0.5],[0.5,0.5],[0.5,2],[0,2],[0,0]]]}
                    """));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static org.locationtech.jts.geom.LineString line(String coordinates) {
        try { return MissionGeometryCodec.parseRoute(new ObjectMapper().readTree("{\"type\":\"LineString\",\"coordinates\":" + coordinates + "}")); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
