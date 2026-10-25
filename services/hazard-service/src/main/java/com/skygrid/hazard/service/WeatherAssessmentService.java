package com.skygrid.hazard.service;

import com.skygrid.hazard.api.WeatherAssessmentRequest;
import com.skygrid.hazard.api.WeatherAssessmentRequest.AssessmentType;
import com.skygrid.hazard.api.WeatherAssessmentResponse;
import com.skygrid.hazard.api.WeatherAssessmentResponse.Coordinate;
import com.skygrid.hazard.api.WeatherAssessmentResponse.FieldSummary;
import com.skygrid.hazard.api.WeatherAssessmentResponse.ForecastPoint;
import com.skygrid.hazard.api.WeatherAssessmentResponse.Sample;
import com.skygrid.hazard.api.WeatherAssessmentResponse.Thresholds;
import com.skygrid.hazard.api.WeatherAssessmentResponse.Warning;
import com.skygrid.hazard.config.WeatherProperties;
import com.skygrid.hazard.domain.AssessmentGeometry;
import com.skygrid.hazard.domain.AssessmentLocations;
import com.skygrid.hazard.provider.WeatherProvider;
import com.skygrid.hazard.provider.WeatherProviderException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Service
public class WeatherAssessmentService {
    private final WeatherProvider provider;
    private final WeatherProperties properties;

    public WeatherAssessmentService(WeatherProvider provider, WeatherProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    public WeatherAssessmentResponse assess(WeatherAssessmentRequest request) {
        if (request.assessmentType() == null || request.geometry() == null || request.geometry().isNull())
            throw new WeatherProviderException(400, "assessmentType and geometry are required.");
        int horizon = request.forecastHours() == null ? Math.min(24, properties.maxForecastHours()) : request.forecastHours();
        if (horizon < 1 || horizon > properties.maxForecastHours())
            throw new WeatherProviderException(400, "forecastHours must be between 1 and the configured maximum of " + properties.maxForecastHours() + ".");
        try {
            if (request.geometry().toString().length() > 65_536) throw new WeatherProviderException(400, "Geometry request exceeds the 64 KiB limit.");
        } catch (WeatherProviderException e) { throw e; }
        catch (RuntimeException failure) { throw new WeatherProviderException(400, "Geometry request is malformed."); }

        AssessmentLocations locations = AssessmentGeometry.locations(request, properties);
        List<WeatherProvider.Forecast> forecasts = provider.forecast(locations.coordinates(), horizon);
        Instant retrievedAt = Instant.now();
        if (forecasts.size() != locations.coordinates().size()) throw new WeatherProviderException(502, "Weather provider location response could not be matched to the assessment.");

        List<Sample> samples = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        List<List<ForecastPoint>> allSamples = new ArrayList<>();
        boolean missing = false;
        Instant until = retrievedAt.plusSeconds(horizon * 3600L);
        for (int sampleIndex = 0; sampleIndex < forecasts.size(); sampleIndex++) {
            WeatherProvider.Forecast forecast = forecasts.get(sampleIndex);
            if (!isUtcTimezone(forecast.timezone())) throw new WeatherProviderException(502, "Weather provider did not honor the requested UTC timezone.");
            List<ForecastPoint> points = forecast.values().stream()
                    .filter(value -> !value.validAt().isBefore(retrievedAt.minusSeconds(3600)) && !value.validAt().isAfter(until))
                    .sorted(Comparator.comparing(WeatherProvider.ForecastValue::validAt))
                    .map(value -> new ForecastPoint(value.validAt(), value.temperatureC(), value.windSpeedMs(),
                            value.windGustMs(), value.precipitationMm())).toList();
            if (points.size() < horizon) missing = true;
            for (ForecastPoint point : points) {
                missing |= point.temperatureC() == null || point.windSpeedMs() == null || point.windGustMs() == null || point.precipitationMm() == null;
                addWarning(warnings, "WIND_SPEED_HIGH", "windSpeed", properties.thresholds().windSpeedMs(), "m/s", point.windSpeedMs(), point,
                        locations.coordinates().get(sampleIndex), sampleIndex, "Sustained 10 m wind forecast is at or above the general planning threshold.");
                addWarning(warnings, "WIND_GUST_HIGH", "windGust", properties.thresholds().windGustMs(), "m/s", point.windGustMs(), point,
                        locations.coordinates().get(sampleIndex), sampleIndex, "10 m wind gust forecast is at or above the general planning threshold.");
                addWarning(warnings, "PRECIPITATION_HIGH", "precipitation", properties.thresholds().precipitationMmPrecedingHour(), "mm", point.precipitationMm(), point,
                        locations.coordinates().get(sampleIndex), sampleIndex, "Preceding-hour precipitation accumulation is at or above the general planning threshold.");
            }
            if (points.isEmpty()) missing = true;
            allSamples.add(points);
            samples.add(new Sample(sampleIndex + 1, locations.coordinates().get(sampleIndex), points));
        }
        List<String> limitations = new ArrayList<>(locations.limitations());
        limitations.add("Weather is supplied by Open-Meteo and may be delayed or model-derived; no aircraft-specific operating limits are applied.");
        limitations.add("Forecast data is not a live observation, aviation weather briefing, safety guarantee, or flight clearance.");
        if (missing) limitations.add("Some forecast values or times were unavailable; missing values are left null and are not treated as zero.");
        return new WeatherAssessmentResponse(UUID.randomUUID(), missing ? "PARTIAL" : "COMPLETE", "Open-Meteo Forecast API",
                retrievedAt, "UTC", horizon, locations.strategy(),
                new Thresholds(properties.thresholds().windSpeedMs(), properties.thresholds().windGustMs(), properties.thresholds().precipitationMmPrecedingHour()),
                samples, summaries(allSamples), warnings, List.copyOf(limitations));
    }

    private static void addWarning(List<Warning> warnings, String code, String field, double threshold, String unit,
                                   Double measured, ForecastPoint point, Coordinate coordinate, int sampleIndex, String explanation) {
        if (measured != null && measured >= threshold)
            warnings.add(new Warning(code, field, threshold, unit, measured, point.validAt(), coordinate, sampleIndex + 1,
                    explanation + " This is a general planning indicator, not an aircraft limit."));
    }

    private static boolean isUtcTimezone(String timezone) {
        return timezone != null && (timezone.equalsIgnoreCase("UTC") || timezone.equalsIgnoreCase("GMT")
                || timezone.equalsIgnoreCase("Etc/UTC") || timezone.equalsIgnoreCase("Etc/GMT"));
    }

    private static List<FieldSummary> summaries(List<List<ForecastPoint>> samples) {
        return List.of(summary("temperature", "°C", ForecastPoint::temperatureC, samples),
                summary("windSpeed", "m/s", ForecastPoint::windSpeedMs, samples),
                summary("windGust", "m/s", ForecastPoint::windGustMs, samples),
                summary("precipitation", "mm", ForecastPoint::precipitationMm, samples));
    }

    private static FieldSummary summary(String name, String unit, Function<ForecastPoint, Double> value, List<List<ForecastPoint>> samples) {
        double[] values = samples.stream().flatMap(List::stream).map(value).filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        double[] sampleMeans = samples.stream().map(sample -> sample.stream().map(value).filter(java.util.Objects::nonNull)
                        .mapToDouble(Double::doubleValue).average().orElse(Double.NaN)).filter(Double::isFinite).mapToDouble(Double::doubleValue).toArray();
        if (values.length == 0) return new FieldSummary(name, unit, null, null, null, null, null, null);
        double min = java.util.Arrays.stream(values).min().orElseThrow(), max = java.util.Arrays.stream(values).max().orElseThrow();
        Double sampleMin = sampleMeans.length == 0 ? null : java.util.Arrays.stream(sampleMeans).min().orElseThrow();
        Double sampleMax = sampleMeans.length == 0 ? null : java.util.Arrays.stream(sampleMeans).max().orElseThrow();
        return new FieldSummary(name, unit, min, max, max - min, sampleMin, sampleMax,
                sampleMin == null ? null : sampleMax - sampleMin);
    }
}
