package com.skygrid.hazard.domain;

import com.skygrid.hazard.api.WeatherAssessmentResponse.Coordinate;
import java.util.List;

public record AssessmentLocations(List<Coordinate> coordinates, String strategy, List<String> limitations) {}
