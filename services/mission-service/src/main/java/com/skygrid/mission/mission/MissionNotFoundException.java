package com.skygrid.mission.mission;

import java.util.UUID;

public class MissionNotFoundException extends RuntimeException {

    public MissionNotFoundException(UUID id) {
        super("Mission not found: " + id);
    }
}
