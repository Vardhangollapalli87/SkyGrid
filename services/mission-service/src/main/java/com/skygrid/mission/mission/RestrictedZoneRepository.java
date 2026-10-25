package com.skygrid.mission.mission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RestrictedZoneRepository extends JpaRepository<RestrictedZone, UUID> {

    @Query(value = """
            select z.id as id, z.name as name, z.demonstration as demonstration
            from restricted_zones z
            join missions m on m.id = :missionId
            where m.geometry is not null
              and ST_Intersects(z.geometry, m.geometry)
            order by z.name, z.id
            """, nativeQuery = true)
    List<RestrictedZoneIntersection> findIntersectionsForMission(@Param("missionId") UUID missionId);

    interface RestrictedZoneIntersection {
        UUID getId();

        String getName();

        boolean getDemonstration();
    }
}
