package com.skygrid.hazard.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.skygrid.hazard.api.WeatherAssessmentRequest;
import com.skygrid.hazard.config.WeatherProperties;
import com.skygrid.hazard.provider.WeatherProviderException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.valid.IsValidOp;

import java.util.ArrayList;
import java.util.List;

public final class AssessmentGeometry {
    private static final int SRID = 4326;
    private static final int MAX_POLYGON_POSITIONS = 1000;
    private static final int MAX_ROUTE_WAYPOINTS = 64;
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), SRID);

    private AssessmentGeometry() {}

    public static AssessmentLocations locations(WeatherAssessmentRequest request, WeatherProperties properties) {
        JsonNode geometry = request.geometry();
        if (!geometry.isObject()) throw invalid("geometry must be a GeoJSON geometry object.");
        return switch (request.assessmentType()) {
            case POINT -> point(geometry);
            case POLYGON -> polygon(geometry);
            case ROUTE -> route(geometry, properties);
        };
    }

    private static AssessmentLocations point(JsonNode geometry) {
        if (!"Point".equals(geometry.path("type").asText())) throw invalid("POINT assessment requires GeoJSON Point geometry.");
        Coordinate coordinate = position(geometry.path("coordinates"));
        return new AssessmentLocations(List.of(api(coordinate)), "Single coordinate point forecast.",
                List.of("Point forecasts represent a grid-cell estimate, not an exact observation at the requested coordinate."));
    }

    private static AssessmentLocations polygon(JsonNode geometry) {
        if (!"Polygon".equals(geometry.path("type").asText())) throw invalid("POLYGON assessment requires GeoJSON Polygon geometry.");
        JsonNode rings = geometry.path("coordinates");
        if (!rings.isArray() || rings.isEmpty()) throw invalid("Polygon requires an exterior ring.");
        int count = 0;
        LinearRing shell = ring(rings.get(0)); count += rings.get(0).size();
        LinearRing[] holes = new LinearRing[Math.max(0, rings.size() - 1)];
        for (int i = 1; i < rings.size(); i++) { count += rings.get(i).size(); holes[i - 1] = ring(rings.get(i)); }
        if (count > MAX_POLYGON_POSITIONS) throw invalid("Polygon exceeds the maximum of 1,000 coordinate positions.");
        Polygon polygon = FACTORY.createPolygon(shell, holes);
        if (polygon.isEmpty() || !new IsValidOp(polygon).isValid()) throw invalid("Polygon is empty or topologically invalid.");
        Coordinate sample = polygon.getInteriorPoint().getCoordinate();
        return new AssessmentLocations(List.of(api(sample)), "Forecast at the JTS interior point of the supplied Polygon.",
                List.of("A single representative point does not describe weather throughout the entire polygon."));
    }

    private static AssessmentLocations route(JsonNode geometry, WeatherProperties properties) {
        if (!"LineString".equals(geometry.path("type").asText())) throw invalid("ROUTE assessment requires GeoJSON LineString geometry.");
        JsonNode coordinates = geometry.path("coordinates");
        if (!coordinates.isArray() || coordinates.size() < 2 || coordinates.size() > MAX_ROUTE_WAYPOINTS)
            throw invalid("Route must contain 2 to 64 ordered waypoints.");
        List<Coordinate> points = new ArrayList<>();
        for (JsonNode position : coordinates) {
            Coordinate point = position(position);
            if (!points.isEmpty() && point.equals2D(points.getLast())) throw invalid("Consecutive route waypoints must not be identical.");
            points.add(point);
        }
        double[] cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) cumulative[i] = cumulative[i - 1] + distanceMeters(points.get(i - 1), points.get(i));
        double length = cumulative[cumulative.length - 1];
        if (length <= 0) throw invalid("Route length must be greater than zero.");
        if (length > properties.maxRouteLengthKm() * 1000) throw invalid("Route exceeds configured maximum length of " + properties.maxRouteLengthKm() + " km.");
        int samples = Math.min(properties.maxRouteSamples(), Math.max(2,
                (int) Math.ceil(length / (properties.sampleSpacingKm() * 1000)) + 1));
        List<Coordinate> sampled = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            Coordinate sample = atDistance(points, cumulative, length * i / (samples - 1));
            if (sampled.isEmpty() || distanceMeters(sampled.getLast(), sample) > 0.01) sampled.add(sample);
        }
        if (sampled.size() == 1) sampled.add(points.getLast());
        double actualSpacingKm = length / (sampled.size() - 1) / 1000;
        return new AssessmentLocations(sampled.stream().map(AssessmentGeometry::api).toList(),
                "Deterministic geodesic samples distributed uniformly along the complete route; " + sampled.size() + " samples, spacing approximately " + String.format(java.util.Locale.ROOT, "%.2f", actualSpacingKm) + " km.",
                List.of("Route assessment samples weather at discrete points; conditions between samples may differ.",
                        "Ground weather forecasts do not account for aircraft altitude, terrain, obstacles, aircraft limits, or flight-time exposure."));
    }

    private static LinearRing ring(JsonNode ring) {
        if (!ring.isArray() || ring.size() < 4) throw invalid("Each polygon ring must have at least four positions.");
        Coordinate[] points = new Coordinate[ring.size()];
        for (int i = 0; i < ring.size(); i++) points[i] = position(ring.get(i));
        if (!points[0].equals2D(points[points.length - 1])) throw invalid("Polygon rings must be closed.");
        try { return FACTORY.createLinearRing(points); }
        catch (IllegalArgumentException e) { throw invalid("Polygon ring is malformed."); }
    }

    private static Coordinate position(JsonNode value) {
        if (!value.isArray() || value.size() != 2 || !value.get(0).isNumber() || !value.get(1).isNumber())
            throw invalid("Each position must contain exactly longitude and latitude numbers.");
        double longitude = value.get(0).doubleValue(), latitude = value.get(1).doubleValue();
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) throw invalid("Longitude must be finite and between -180 and 180.");
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) throw invalid("Latitude must be finite and between -90 and 90.");
        return new Coordinate(longitude, latitude);
    }

    private static Coordinate atDistance(List<Coordinate> points, double[] cumulative, double distance) {
        if (distance <= 0) return points.getFirst();
        if (distance >= cumulative[cumulative.length - 1]) return points.getLast();
        int segment = 1;
        while (cumulative[segment] < distance) segment++;
        double fraction = (distance - cumulative[segment - 1]) / (cumulative[segment] - cumulative[segment - 1]);
        return interpolate(points.get(segment - 1), points.get(segment), fraction);
    }

    private static Coordinate interpolate(Coordinate a, Coordinate b, double fraction) {
        double[] u = vector(a), v = vector(b);
        double dot = Math.max(-1, Math.min(1, u[0]*v[0] + u[1]*v[1] + u[2]*v[2]));
        double omega = Math.acos(dot);
        if (omega < 1e-12) return new Coordinate(a.x, a.y);
        double sin = Math.sin(omega);
        double left = Math.sin((1 - fraction) * omega) / sin, right = Math.sin(fraction * omega) / sin;
        double x = left*u[0] + right*v[0], y = left*u[1] + right*v[1], z = left*u[2] + right*v[2];
        return new Coordinate(Math.toDegrees(Math.atan2(y, x)), Math.toDegrees(Math.atan2(z, Math.hypot(x, y))));
    }

    private static double[] vector(Coordinate c) {
        double lon = Math.toRadians(c.x), lat = Math.toRadians(c.y);
        return new double[]{Math.cos(lat)*Math.cos(lon), Math.cos(lat)*Math.sin(lon), Math.sin(lat)};
    }

    public static double distanceMeters(Coordinate a, Coordinate b) {
        final double radius = 6_371_008.8;
        double lat1 = Math.toRadians(a.y), lat2 = Math.toRadians(b.y);
        double dLat = lat2 - lat1, dLon = Math.toRadians(b.x - a.x);
        double haversine = Math.sin(dLat/2)*Math.sin(dLat/2) + Math.cos(lat1)*Math.cos(lat2)*Math.sin(dLon/2)*Math.sin(dLon/2);
        return 2 * radius * Math.asin(Math.sqrt(Math.min(1, haversine)));
    }
    private static com.skygrid.hazard.api.WeatherAssessmentResponse.Coordinate api(Coordinate c) { return new com.skygrid.hazard.api.WeatherAssessmentResponse.Coordinate(c.x, c.y); }
    private static WeatherProviderException invalid(String message) { return new WeatherProviderException(400, message); }
}
