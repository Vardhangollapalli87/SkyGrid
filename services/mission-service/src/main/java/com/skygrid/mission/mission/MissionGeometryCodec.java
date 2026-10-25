package com.skygrid.mission.mission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
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
