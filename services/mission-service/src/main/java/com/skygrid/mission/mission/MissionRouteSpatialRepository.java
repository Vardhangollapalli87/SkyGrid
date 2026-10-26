package com.skygrid.mission.mission;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MissionRouteSpatialRepository {
    private final JdbcTemplate jdbcTemplate;

    public MissionRouteSpatialRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public SpatialResult checkContainment(UUID missionId, UUID routeId) {
        return jdbcTemplate.queryForObject("""
                SELECT ST_Covers(area.geometry, route.route) AS covered,
                       ST_Intersects(area.geometry, route.route) AS intersects,
                       ST_AsGeoJSON(ST_CollectionExtract(ST_Difference(route.route, area.geometry), 2)) AS outside_geojson,
                       md5(encode(ST_AsBinary(area.geometry), 'hex')) AS mission_geometry_hash,
                       md5(encode(ST_AsBinary(route.route), 'hex')) AS route_geometry_hash
                  FROM missions area
                  JOIN missions route ON route.id = ?
                 WHERE area.id = ? AND route.associated_mission_id = area.id
                """, (rs, row) -> new SpatialResult(rs.getBoolean("covered"), rs.getBoolean("intersects"), rs.getString("outside_geojson"),
                rs.getString("mission_geometry_hash"), rs.getString("route_geometry_hash")), routeId, missionId);
    }

    public record SpatialResult(boolean covered, boolean intersects, String outsideGeoJson, String missionGeometryHash, String routeGeometryHash) { }
}
