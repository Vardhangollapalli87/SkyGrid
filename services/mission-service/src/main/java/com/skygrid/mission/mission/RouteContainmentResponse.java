package com.skygrid.mission.mission;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record RouteContainmentResponse(
        UUID missionId,
        UUID routeId,
        Status status,
        String boundarySemantics,
        String message,
        Instant checkedAt,
        String missionGeometryHash,
        String routeGeometryHash,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode outsideGeometry
) {
    public enum Status { WITHIN_OR_ON_BOUNDARY, PARTLY_OUTSIDE, OUTSIDE, INVALID_GEOMETRY, MISSING_GEOMETRY, UNAVAILABLE }
}
