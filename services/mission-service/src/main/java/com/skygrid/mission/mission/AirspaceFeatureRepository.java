package com.skygrid.mission.mission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface AirspaceFeatureRepository extends JpaRepository<AirspaceFeature, UUID> {
    @Query(value = """
        select f.id, f.source_id, f.source_name, f.country_code, f.dataset_name, f.name, f.category, f.classification,
               f.source_properties, f.geometry_status, f.source_effective_at, f.imported_at,
               ST_AsGeoJSON(f.geometry)::json as geometry
          from airspace_features f
          join airspace_dataset_imports d on d.country_code=f.country_code and d.source_name=f.source_name
            and d.dataset_name=f.dataset_name and d.status in ('SUCCESS','PARTIAL')
         where f.geometry is not null
           and f.geometry && ST_MakeEnvelope(:west, :south, :east, :north, 4326)
           and ST_Intersects(f.geometry, ST_MakeEnvelope(:west, :south, :east, :north, 4326))
         order by f.country_code, f.source_id
         limit 2001
        """, nativeQuery = true)
    List<Object[]> findInBounds(@Param("west") double west, @Param("south") double south,
                                @Param("east") double east, @Param("north") double north);

    @Query(value = """
        select country_code, source_name, dataset_name, category, spatial_count, unconverted_count,
               imported_at, coverage_verified, limitations, input_count, rejected_count, duplicate_count, status,
               source_url, source_effective_at, last_attempt_at, inserted_count, updated_count, skipped_count,
               non_spatial_count, failed_count
          from airspace_dataset_imports order by country_code, source_name
        """, nativeQuery = true)
    List<Object[]> inventory();

    @Query(value = """
        select z.id as id, coalesce(z.name, z.source_id) as name, false as demonstration
          from airspace_features z join missions m on m.id = :missionId
         where z.geometry is not null and z.category = 'RESTRICTION'
           and z.geometry_status = 'VALID'
           and exists (select 1 from airspace_dataset_imports d where d.country_code=z.country_code
             and d.source_name=z.source_name and d.dataset_name=z.dataset_name and d.status in ('SUCCESS','PARTIAL'))
           and ST_Intersects(z.geometry, m.geometry)
         order by z.country_code, z.source_id
        """, nativeQuery = true)
    List<RestrictedZoneRepository.RestrictedZoneIntersection> findRestrictionIntersections(@Param("missionId") UUID missionId);
}
