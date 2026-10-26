package com.skygrid.mission.mission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

public interface MissionRepository extends JpaRepository<Mission, UUID> {
    List<Mission> findByAssociatedMission_IdOrderByCreatedAtDesc(UUID missionId);

    List<Mission> findByAssociatedMissionIsNullAndRouteIsNotNullOrderByCreatedAtDesc();
}
