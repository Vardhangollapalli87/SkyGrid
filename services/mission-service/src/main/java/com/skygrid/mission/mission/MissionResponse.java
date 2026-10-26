package com.skygrid.mission.mission;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record MissionResponse(UUID id, String name, String description, Instant createdAt,
                              @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode geometry,
                              @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode route,
                              @JsonInclude(JsonInclude.Include.NON_NULL) UUID associatedMissionId) {

    public MissionResponse(UUID id, String name, String description, Instant createdAt) {
        this(id, name, description, createdAt, null, null, null);
    }

    public MissionResponse(UUID id, String name, String description, Instant createdAt, JsonNode geometry) {
        this(id, name, description, createdAt, geometry, null, null);
    }

    public MissionResponse(UUID id, String name, String description, Instant createdAt, JsonNode geometry, JsonNode route) {
        this(id, name, description, createdAt, geometry, route, null);
    }

    static MissionResponse from(Mission mission) {
        return new MissionResponse(
                mission.getId(),
                mission.getName(),
                mission.getDescription(),
                mission.getCreatedAt(),
                MissionGeometryCodec.toGeoJson(mission.getGeometry()),
                MissionGeometryCodec.toGeoJson(mission.getRoute()),
                mission.getAssociatedMissionId()
        );
    }

    @Override
    public String toString() {
        return "MissionResponse[fields redacted]";
    }
}
