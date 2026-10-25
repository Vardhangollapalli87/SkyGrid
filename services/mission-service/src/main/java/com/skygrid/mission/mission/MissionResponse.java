package com.skygrid.mission.mission;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record MissionResponse(UUID id, String name, String description, Instant createdAt,
                              @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode geometry) {

    public MissionResponse(UUID id, String name, String description, Instant createdAt) {
        this(id, name, description, createdAt, null);
    }

    static MissionResponse from(Mission mission) {
        return new MissionResponse(
                mission.getId(),
                mission.getName(),
                mission.getDescription(),
                mission.getCreatedAt(),
                MissionGeometryCodec.toGeoJson(mission.getGeometry())
        );
    }
}
