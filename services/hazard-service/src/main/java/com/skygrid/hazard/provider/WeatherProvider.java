package com.skygrid.hazard.provider;

import com.skygrid.hazard.api.WeatherAssessmentResponse.Coordinate;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface WeatherProvider {
    List<Forecast> forecast(List<Coordinate> coordinates, int forecastHours);

    record Forecast(List<ForecastValue> values, String timezone) {}
    record ForecastValue(java.time.Instant validAt, Double temperatureC, Double windSpeedMs,
                         Double windGustMs, Double precipitationMm) {}
}
