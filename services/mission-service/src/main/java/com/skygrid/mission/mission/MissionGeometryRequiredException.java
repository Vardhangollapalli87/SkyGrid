package com.skygrid.mission.mission;

public class MissionGeometryRequiredException extends RuntimeException {

    public MissionGeometryRequiredException() {
        super("A mission polygon is required for a restricted-zone check.");
    }
}
