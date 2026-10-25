package com.skygrid.hazard.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("skygrid.weather")
public record WeatherProperties(@NotBlank String baseUrl, Duration connectTimeout, Duration readTimeout,
                                @Min(1) @Max(168) int maxForecastHours,
                                @DecimalMin("1") @DecimalMax("1000") double maxRouteLengthKm,
                                @Min(2) @Max(24) int maxRouteSamples,
                                @DecimalMin("1") @DecimalMax("250") double sampleSpacingKm,
                                @Valid Thresholds thresholds, String corsAllowedOrigins) {
    public record Thresholds(@DecimalMin("0") double windSpeedMs,
                             @DecimalMin("0") double windGustMs,
                             @DecimalMin("0") double precipitationMmPrecedingHour) {}
}
