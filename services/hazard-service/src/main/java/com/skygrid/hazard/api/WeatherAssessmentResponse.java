package com.skygrid.hazard.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WeatherAssessmentResponse(UUID assessmentId, String status, String provider, Instant retrievedAt,
                                        String timezone, int forecastHours, String strategy,
                                        Thresholds thresholds, List<Sample> samples, List<FieldSummary> variation,
                                        List<Warning> warnings, List<String> limitations) {
    public record Coordinate(double longitude, double latitude) {}
    public record Sample(int sampleIndex, Coordinate coordinate, List<ForecastPoint> forecast) {}
    public record ForecastPoint(Instant validAt, Double temperatureC, Double windSpeedMs,
                                Double windGustMs, Double precipitationMm) {}
    public record Thresholds(double windSpeedMs, double windGustMs, double precipitationMmPrecedingHour) {}
    public record FieldSummary(String field, String unit, Double minimum, Double maximum, Double range,
                              Double sampleMinimum, Double sampleMaximum, Double sampleRange) {}
    public record Warning(String code, String field, double threshold, String unit, double measuredValue,
                          Instant validAt, Coordinate coordinate, int sampleIndex, String explanation) {}

    @Override
    public String toString() {
        return "WeatherAssessmentResponse[assessmentId=" + assessmentId + ", status=" + status +
                ", provider=" + provider + ", retrievedAt=" + retrievedAt + ", forecastHours=" + forecastHours +
                ", payload=<redacted>]";
    }
}
