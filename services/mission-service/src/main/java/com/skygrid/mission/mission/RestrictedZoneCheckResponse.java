package com.skygrid.mission.mission;

import java.util.List;
import java.util.UUID;

public record RestrictedZoneCheckResponse(
        UUID missionId,
        boolean intersects,
        String message,
        String dataNotice,
        List<ZoneMatch> zones
) {

    public record ZoneMatch(UUID id, String name, boolean demonstration) {
    }
}
