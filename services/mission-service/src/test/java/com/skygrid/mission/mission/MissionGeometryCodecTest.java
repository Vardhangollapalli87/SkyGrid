package com.skygrid.mission.mission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.LineString;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionGeometryCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesAndSerializesClosedGeoJsonPolygonWithLongitudeFirst() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {"type":"Polygon","coordinates":[[[77.59,12.97],[77.60,12.97],
                 [77.60,12.98],[77.59,12.98],[77.59,12.97]]]}
                """);

        Polygon polygon = MissionGeometryCodec.parse(input);
        JsonNode output = MissionGeometryCodec.toGeoJson(polygon);

        assertEquals(4326, polygon.getSRID());
        assertEquals("Polygon", output.path("type").asText());
        assertEquals(77.59, output.path("coordinates").path(0).path(0).path(0).asDouble());
        assertEquals(12.97, output.path("coordinates").path(0).path(0).path(1).asDouble());
        assertTrue(MissionGeometryCodec.parse(output).equalsExact(polygon));
    }

    @Test
    void rejectsUnsupportedGeometryType() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {"type":"Point","coordinates":[77.59,12.97]}
                """);

        InvalidMissionGeometryException exception = assertThrows(InvalidMissionGeometryException.class,
                () -> MissionGeometryCodec.parse(input));

        assertTrue(exception.getMessage().contains("Polygon"));
    }

    @Test
    void rejectsMalformedCoordinatePositions() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {"type":"Polygon","coordinates":[[[77.59],[77.60,12.97],
                 [77.60,12.98],[77.59,12.97]]]}
                """);

        assertThrows(InvalidMissionGeometryException.class, () -> MissionGeometryCodec.parse(input));
    }

    @Test
    void rejectsLongitudeAndLatitudeOutsideWgs84Ranges() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {"type":"Polygon","coordinates":[[[181,12],[180,12],[180,13],[181,12]]]}
                """);

        assertThrows(InvalidMissionGeometryException.class, () -> MissionGeometryCodec.parse(input));
    }

    @Test
    void rejectsSelfIntersectingPolygon() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {"type":"Polygon","coordinates":[[[0,0],[1,1],[0,1],[1,0],[0,0]]]}
                """);

        assertThrows(InvalidMissionGeometryException.class, () -> MissionGeometryCodec.parse(input));
    }

    @Test
    void rejectsUnclosedAndEmptyPolygonRings() throws Exception {
        JsonNode unclosed = objectMapper.readTree("""
                {"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,1]]]}
                """);
        JsonNode empty = objectMapper.readTree("""
                {"type":"Polygon","coordinates":[]}
                """);

        assertThrows(InvalidMissionGeometryException.class, () -> MissionGeometryCodec.parse(unclosed));
        assertThrows(InvalidMissionGeometryException.class, () -> MissionGeometryCodec.parse(empty));
    }

    @Test
    void parsesAndSerializesOrderedRouteAsWgs84LineString() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {"type":"LineString","coordinates":[[77.59,12.97],[77.60,12.97],[77.60,12.98]]}
                """);
        LineString route = MissionGeometryCodec.parseRoute(input);
        JsonNode output = MissionGeometryCodec.toGeoJson(route);
        assertEquals(4326, route.getSRID());
        assertEquals("LineString", output.path("type").asText());
        assertEquals(77.59, output.path("coordinates").path(0).path(0).asDouble());
        assertEquals(12.97, output.path("coordinates").path(0).path(1).asDouble());
        assertTrue(MissionGeometryCodec.parseRoute(output).equalsExact(route));
    }

    @Test
    void rejectsInsufficientInvalidAndDuplicateRouteWaypoints() throws Exception {
        JsonNode shortRoute = objectMapper.readTree("{\"type\":\"LineString\",\"coordinates\":[[0,0]]}");
        JsonNode outOfRange = objectMapper.readTree("{\"type\":\"LineString\",\"coordinates\":[[181,0],[0,1]]}");
        JsonNode duplicate = objectMapper.readTree("{\"type\":\"LineString\",\"coordinates\":[[0,0],[0,0]]}");
        assertThrows(InvalidMissionGeometryException.class, () -> MissionGeometryCodec.parseRoute(shortRoute));
        assertThrows(InvalidMissionGeometryException.class, () -> MissionGeometryCodec.parseRoute(outOfRange));
        assertThrows(InvalidMissionGeometryException.class, () -> MissionGeometryCodec.parseRoute(duplicate));
    }
}
