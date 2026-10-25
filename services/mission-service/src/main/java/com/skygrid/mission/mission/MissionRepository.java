package com.skygrid.mission.mission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MissionRepository extends JpaRepository<Mission, UUID> {
}
