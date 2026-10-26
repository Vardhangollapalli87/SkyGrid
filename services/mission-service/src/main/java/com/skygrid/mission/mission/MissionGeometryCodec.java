package com.skygrid.mission.mission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.valid.IsValidOp;

import java.util.ArrayList;
import java.util.List;

/** Converts GeoJSON Polygon objects to and from JTS using WGS84 longitude/latitude coordinates. */
final class MissionGeometryCodec {

    private static final int SRID = 4326;
    private static final int MAX_POSITIONS = 10_000;
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), SRID);

    private MissionGeometryCodec() {
    }

    static Polygon parse(JsonNode geometry) {
        if (geometry == null || geometry.isNull()) {
            return null;
        }
        if (!geometry.isObject()) {
            throw invalid("Geometry must be a GeoJSON Polygon object.");
        }
        JsonNode type = geometry.get("type");
        if (type == null || !type.isTextual() || !"Polygon".equals(type.textValue())) {
            throw invalid("Only GeoJSON Polygon geometry is supported.");
        }

        JsonNode coordinates = geometry.get("coordinates");
        if (coordinates == null || !coordinates.isArray() || coordinates.isEmpty()) {
            throw invalid("Polygon coordinates must include an exterior ring.");
        }
        if (coordinates.size() > MAX_POSITIONS) {
            throw invalid("Polygon contains too many rings.");
        }

        int[] positionCount = {0};
        List<LinearRing> rings = new ArrayList<>();
        for (JsonNode ringNode : coordinates) {
            rings.add(parseRing(ringNode, positionCount));
        }

        Polygon polygon;
        try {
            polygon = GEOMETRY_FACTORY.createPolygon(rings.getFirst(),
                    rings.subList(1, rings.size()).toArray(LinearRing[]::new));
        } catch (IllegalArgumentException exception) {
            throw invalid("Polygon rings are malformed.");
        }
        if (polygon.isEmpty()) {
            throw invalid("Polygon must not be empty.");
        }
        if (!new IsValidOp(polygon).isValid()) {
            throw invalid("Polygon topology is invalid or self-intersecting.");
        }
        polygon.setSRID(SRID);
        return polygon;
    }

    static LineString parseRoute(JsonNode route) {
        if (route == null || route.isNull()) return null;
        if (!route.isObject() || !"LineString".equals(route.path("type").asText()))
            throw invalid("Route must be a GeoJSON LineString.");
        JsonNode coordinates = route.get("coordinates");
        if (coordinates == null || !coordinates.isArray() || coordinates.size() < 2 || coordinates.size() > MAX_POSITIONS)
            throw invalid("A route must contain between two and 10,000 ordered waypoints.");
        Coordinate[] points = new Coordinate[coordinates.size()];
        for (int index = 0; index < coordinates.size(); index++) {
            JsonNode position = coordinates.get(index);
            if (!position.isArray() || position.size() != 2 || !position.get(0).isNumber() || !position.get(1).isNumber())
                throw invalid("Each route waypoint must contain exactly longitude and latitude numbers.");
            double longitude = position.get(0).doubleValue(), latitude = position.get(1).doubleValue();
            if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180)
                throw invalid("Route longitude must be between -180 and 180.");
            if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90)
                throw invalid("Route latitude must be between -90 and 90.");
            points[index] = new Coordinate(longitude, latitude);
            if (index > 0 && points[index].equals2D(points[index - 1]))
                throw invalid("Consecutive route waypoints must not be identical.");
        }
        LineString line = GEOMETRY_FACTORY.createLineString(points);
        if (line.isEmpty() || line.getLength() == 0) throw invalid("Route length must be greater than zero.");
        line.setSRID(SRID);
        return line;
    }

    static JsonNode toGeoJson(Polygon polygon) {
        if (polygon == null) {
            return null;
        }
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("type", "Polygon");
        ArrayNode polygonCoordinates = result.putArray("coordinates");
        appendRing(polygonCoordinates, polygon.getExteriorRing().getCoordinates());
        for (int index = 0; index < polygon.getNumInteriorRing(); index++) {
            appendRing(polygonCoordinates, polygon.getInteriorRingN(index).getCoordinates());
        }
        return result;
    }

    static JsonNode toGeoJson(LineString route) {
        if (route == null) return null;
        ObjectNode result = JsonNodeFactory.instance.objectNode(); result.put("type", "LineString");
        ArrayNode coordinates = result.putArray("coordinates");
        for (Coordinate point : route.getCoordinates()) coordinates.addArray().add(point.x).add(point.y);
        return result;
    }

    static boolean validForContainment(Geometry geometry) {
        if (geometry == null || geometry.isEmpty() || geometry.getSRID() != SRID || !new IsValidOp(geometry).isValid()) return false;
        if (geometry instanceof LineString line && line.getLength() == 0) return false;
        for (Coordinate point : geometry.getCoordinates()) {
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || point.x < -180 || point.x > 180 || point.y < -90 || point.y > 90) return false;
        }
        return true;
    }

    private static LinearRing parseRing(JsonNode ringNode, int[] positionCount) {
        if (!ringNode.isArray() || ringNode.size() < 4) {
            throw invalid("Each polygon ring must contain at least four positions.");
        }
        positionCount[0] += ringNode.size();
        if (positionCount[0] > MAX_POSITIONS) {
            throw invalid("Polygon contains too many positions.");
        }

        Coordinate[] points = new Coordinate[ringNode.size()];
        for (int index = 0; index < ringNode.size(); index++) {
            JsonNode position = ringNode.get(index);
            if (!position.isArray() || position.size() != 2
                    || !position.get(0).isNumber() || !position.get(1).isNumber()) {
                throw invalid("Each position must contain exactly longitude and latitude numbers.");
            }
            double longitude = position.get(0).doubleValue();
            double latitude = position.get(1).doubleValue();
            if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
                throw invalid("Longitude must be between -180 and 180.");
            }
            if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
                throw invalid("Latitude must be between -90 and 90.");
            }
            points[index] = new Coordinate(longitude, latitude);
        }
        if (!points[0].equals2D(points[points.length - 1])) {
            throw invalid("Polygon rings must be closed.");
        }
        try {
            return GEOMETRY_FACTORY.createLinearRing(points);
        } catch (IllegalArgumentException exception) {
            throw invalid("Polygon ring is malformed.");
        }
    }

    private static void appendRing(ArrayNode coordinates, Coordinate[] points) {
        ArrayNode ring = coordinates.addArray();
        for (Coordinate point : points) {
            ring.addArray().add(point.x).add(point.y);
        }
    }

    private static InvalidMissionGeometryException invalid(String message) {
        return new InvalidMissionGeometryException(message);
    }
}
