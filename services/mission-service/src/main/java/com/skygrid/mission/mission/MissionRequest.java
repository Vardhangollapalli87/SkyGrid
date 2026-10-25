package com.skygrid.mission.mission;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MissionRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        JsonNode geometry,

        JsonNode route
) {

    public MissionRequest(String name, String description) {
        this(name, description, null, null);
    }

    public MissionRequest(String name, String description, JsonNode geometry) {
        this(name, description, geometry, null);
    }

    @Override
    public String toString() {
        return "MissionRequest[fields redacted]";
    }
}
