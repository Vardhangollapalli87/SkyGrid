package com.skygrid.hazard.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WeatherAssessmentRequest(@NotNull AssessmentType assessmentType,
                                       @NotNull JsonNode geometry,
                                       @Min(1) @Max(168) Integer forecastHours) {
    public enum AssessmentType { POINT, POLYGON, ROUTE }

    @Override
    public String toString() {
        return "WeatherAssessmentRequest[assessmentType=" + assessmentType + ", geometry=<redacted>, forecastHours=" + forecastHours + "]";
    }
}
