package com.skygrid.hazard.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skygrid.hazard.api.WeatherAssessmentRequest;
import com.skygrid.hazard.config.WeatherProperties;
import com.skygrid.hazard.provider.WeatherProviderException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssessmentGeometryTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WeatherProperties properties = new WeatherProperties("https://weather.invalid", java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1),
            72, 250, 12, 25, new WeatherProperties.Thresholds(12, 17, 2), "http://localhost:5173");

    @Test void validatesPointAndPolygonRepresentativePoint() throws Exception {
        var point = new WeatherAssessmentRequest(WeatherAssessmentRequest.AssessmentType.POINT,
                mapper.readTree("{\"type\":\"Point\",\"coordinates\":[77.5,12.9]}"), 24);
        assertEquals(77.5, AssessmentGeometry.locations(point, properties).coordinates().getFirst().longitude());
        var polygon = new WeatherAssessmentRequest(WeatherAssessmentRequest.AssessmentType.POLYGON,
                mapper.readTree("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[2,0],[2,2],[0,2],[0,0]]]}"), 24);
        var representative = AssessmentGeometry.locations(polygon, properties);
        assertTrue(representative.coordinates().getFirst().longitude() > 0 && representative.coordinates().getFirst().longitude() < 2);
        assertTrue(representative.limitations().getFirst().contains("single representative point"));
    }

    @Test void samplesEntireRouteAndHandlesAntimeridian() throws Exception {
        var request = new WeatherAssessmentRequest(WeatherAssessmentRequest.AssessmentType.ROUTE,
                mapper.readTree("{\"type\":\"LineString\",\"coordinates\":[[179,0],[-179,0]]}"), 24);
        var samples = AssessmentGeometry.locations(request, properties);
        assertTrue(samples.coordinates().size() >= 2);
        assertEquals(179, samples.coordinates().getFirst().longitude(), 1e-6);
        assertEquals(-179, samples.coordinates().getLast().longitude(), 1e-6);
        assertTrue(samples.strategy().contains("complete route"));
    }

    @Test void rejectsInvalidCoordinatesDuplicateRouteAndTooLongRoute() throws Exception {
        var badPoint = new WeatherAssessmentRequest(WeatherAssessmentRequest.AssessmentType.POINT,
                mapper.readTree("{\"type\":\"Point\",\"coordinates\":[181,0]}"), 24);
        assertThrows(WeatherProviderException.class, () -> AssessmentGeometry.locations(badPoint, properties));
        var duplicate = new WeatherAssessmentRequest(WeatherAssessmentRequest.AssessmentType.ROUTE,
                mapper.readTree("{\"type\":\"LineString\",\"coordinates\":[[0,0],[0,0]]}"), 24);
        assertThrows(WeatherProviderException.class, () -> AssessmentGeometry.locations(duplicate, properties));
        var longRoute = new WeatherAssessmentRequest(WeatherAssessmentRequest.AssessmentType.ROUTE,
                mapper.readTree("{\"type\":\"LineString\",\"coordinates\":[[0,0],[10,0]]}"), 24);
        assertThrows(WeatherProviderException.class, () -> AssessmentGeometry.locations(longRoute, properties));
        var badLatitude = new WeatherAssessmentRequest(WeatherAssessmentRequest.AssessmentType.POINT,
                mapper.readTree("{\"type\":\"Point\",\"coordinates\":[0,91]}"), 24);
        assertThrows(WeatherProviderException.class, () -> AssessmentGeometry.locations(badLatitude, properties));
        var malformedPolygon = new WeatherAssessmentRequest(WeatherAssessmentRequest.AssessmentType.POLYGON,
                mapper.readTree("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1]]]}"), 24);
        assertThrows(WeatherProviderException.class, () -> AssessmentGeometry.locations(malformedPolygon, properties));
    }

    @Test void boundsRouteSampleCountAndRejectsTooManyWaypoints() throws Exception {
        var limited = new WeatherProperties("https://weather.invalid", java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1),
                72, 250, 3, 5, new WeatherProperties.Thresholds(12, 17, 2), "http://localhost:5173");
        var route = new WeatherAssessmentRequest(WeatherAssessmentRequest.AssessmentType.ROUTE,
                mapper.readTree("{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,0]]}"), 24);
        assertEquals(3, AssessmentGeometry.locations(route, limited).coordinates().size());
        StringBuilder coordinates = new StringBuilder("\"type\":\"LineString\",\"coordinates\":[");
        for (int i = 0; i < 65; i++) {
            if (i > 0) coordinates.append(',');
            coordinates.append('[').append(i * 0.001).append(",0]");
        }
        coordinates.append("]}");
        var tooMany = new WeatherAssessmentRequest(WeatherAssessmentRequest.AssessmentType.ROUTE, mapper.readTree("{" + coordinates), 24);
        assertThrows(WeatherProviderException.class, () -> AssessmentGeometry.locations(tooMany, properties));
    }
}
