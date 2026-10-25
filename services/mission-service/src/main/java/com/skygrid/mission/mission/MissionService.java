package com.skygrid.mission.mission;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MissionService {

    private final MissionRepository missionRepository;
    private final RestrictedZoneRepository restrictedZoneRepository;
    private final AirspaceFeatureRepository airspaceFeatureRepository;

    public MissionService(MissionRepository missionRepository, RestrictedZoneRepository restrictedZoneRepository,
                          AirspaceFeatureRepository airspaceFeatureRepository) {
        this.missionRepository = missionRepository;
        this.restrictedZoneRepository = restrictedZoneRepository;
        this.airspaceFeatureRepository = airspaceFeatureRepository;
    }

    @Transactional(readOnly = true)
    public Page<MissionResponse> listMissions(Pageable pageable) {
        return missionRepository.findAll(pageable).map(MissionResponse::from);
    }

    public MissionResponse createMission(MissionRequest request) {
        if (request.geometry() != null && !request.geometry().isNull() && request.route() != null && !request.route().isNull())
            throw new InvalidMissionGeometryException("Provide either a Polygon mission area or a LineString route, not both.");
        Mission mission = new Mission(UUID.randomUUID(), request.name().trim(), request.description(), Instant.now(),
                MissionGeometryCodec.parse(request.geometry()), MissionGeometryCodec.parseRoute(request.route()));
        return MissionResponse.from(missionRepository.save(mission));
    }

    public MissionResponse updateMission(UUID id, MissionRequest request) {
        if (request.geometry() != null && !request.geometry().isNull() && request.route() != null && !request.route().isNull())
            throw new InvalidMissionGeometryException("Provide either a Polygon mission area or a LineString route, not both.");
        Mission mission = missionRepository.findById(id).orElseThrow(() -> new MissionNotFoundException(id));
        mission.update(request.name().trim(), request.description(), MissionGeometryCodec.parse(request.geometry()),
                MissionGeometryCodec.parseRoute(request.route()));
        return MissionResponse.from(missionRepository.save(mission));
    }

    @Transactional(readOnly = true)
    public MissionResponse getMission(UUID id) {
        return missionRepository.findById(id)
                .map(MissionResponse::from)
                .orElseThrow(() -> new MissionNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public RestrictedZoneCheckResponse checkRestrictedZones(UUID id) {
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> new MissionNotFoundException(id));
        if (mission.getGeometry() == null) {
            throw new MissionGeometryRequiredException();
        }
        List<RestrictedZoneCheckResponse.ZoneMatch> zones = new java.util.ArrayList<>(restrictedZoneRepository
                .findIntersectionsForMission(id).stream()
                .map(zone -> new RestrictedZoneCheckResponse.ZoneMatch(
                        zone.getId(), zone.getName(), zone.getDemonstration()))
                .toList());
        zones.addAll(airspaceFeatureRepository.findRestrictionIntersections(id).stream()
                .map(zone -> new RestrictedZoneCheckResponse.ZoneMatch(zone.getId(), zone.getName(), false)).toList());
        boolean intersects = !zones.isEmpty();
        String message = intersects
                ? "Mission polygon intersects one or more stored restricted-zone polygons."
                : "No stored restricted-zone polygons intersect the mission polygon.";
        return new RestrictedZoneCheckResponse(id, intersects, message,
                "Imported airspace coverage is partial and unverified. A missing match is not clearance; confirm current official aviation sources.",
                zones);
    }
}
