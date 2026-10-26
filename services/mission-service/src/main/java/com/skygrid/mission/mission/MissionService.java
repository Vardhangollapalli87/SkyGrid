package com.skygrid.mission.mission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
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
    private final MissionRouteSpatialRepository routeSpatialRepository;
    private final ObjectMapper objectMapper;

    public MissionService(MissionRepository missionRepository, RestrictedZoneRepository restrictedZoneRepository,
                          AirspaceFeatureRepository airspaceFeatureRepository,
                          MissionRouteSpatialRepository routeSpatialRepository, ObjectMapper objectMapper) {
        this.missionRepository = missionRepository;
        this.restrictedZoneRepository = restrictedZoneRepository;
        this.airspaceFeatureRepository = airspaceFeatureRepository;
        this.routeSpatialRepository = routeSpatialRepository;
        this.objectMapper = objectMapper;
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
        if (mission.getAssociatedMission() != null && (request.route() == null || request.route().isNull()))
            throw new RouteAssociationException("An associated route must retain its LineString. Use the route association endpoint to detach it.");
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
    public List<MissionResponse> listAssociatedRoutes(UUID missionId) {
        requireMissionArea(missionId);
        return missionRepository.findByAssociatedMission_IdOrderByCreatedAtDesc(missionId).stream().map(MissionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MissionResponse> listStandaloneRoutes() {
        return missionRepository.findByAssociatedMissionIsNullAndRouteIsNotNullOrderByCreatedAtDesc().stream().map(MissionResponse::from).toList();
    }

    public MissionResponse createAssociatedRoute(UUID missionId, MissionRequest request) {
        Mission area = requireMissionArea(missionId);
        var line = requireRouteRequest(request);
        Mission route = new Mission(UUID.randomUUID(), request.name().trim(), request.description(), Instant.now(), null, line);
        route.associateWith(area);
        return MissionResponse.from(missionRepository.save(route));
    }

    public MissionResponse updateAssociatedRoute(UUID missionId, UUID routeId, MissionRequest request) {
        Mission area = requireMissionArea(missionId);
        Mission route = requireRoute(routeId);
        if (!missionId.equals(route.getAssociatedMissionId()))
            throw new RouteAssociationException("This route is not associated with the selected mission area.");
        route.update(request.name().trim(), request.description(), null, requireRouteRequest(request));
        route.associateWith(area);
        return MissionResponse.from(missionRepository.save(route));
    }

    public MissionResponse associateRoute(UUID missionId, UUID routeId) {
        Mission area = requireMissionArea(missionId);
        Mission route = requireRoute(routeId);
        if (route.getId().equals(missionId)) throw new RouteAssociationException("A mission area cannot be associated with itself as a route.");
        route.associateWith(area);
        return MissionResponse.from(missionRepository.save(route));
    }

    public MissionResponse disassociateRoute(UUID routeId) {
        Mission route = requireRoute(routeId);
        route.associateWith(null);
        return MissionResponse.from(missionRepository.save(route));
    }

    public MissionResponse clearAssociatedRoute(UUID missionId, UUID routeId) {
        requireMissionArea(missionId);
        Mission route = requireRoute(routeId);
        if (!missionId.equals(route.getAssociatedMissionId()))
            throw new RouteAssociationException("This route is not associated with the selected mission area.");
        route.update(route.getName(), route.getDescription(), null, null);
        route.associateWith(null);
        return MissionResponse.from(missionRepository.save(route));
    }

    @Transactional(readOnly = true)
    public RouteContainmentResponse checkRouteContainment(UUID missionId, UUID routeId) {
        Mission area = missionRepository.findById(missionId).orElseThrow(() -> new MissionNotFoundException(missionId));
        Mission route = missionRepository.findById(routeId).orElseThrow(() -> new MissionNotFoundException(routeId));
        if (!missionId.equals(route.getAssociatedMissionId()))
            throw new RouteAssociationException("Associate this saved route with the mission area before checking containment.");
        Instant checkedAt = Instant.now();
        if (area.getGeometry() == null || route.getRoute() == null)
            return containment(missionId, routeId, RouteContainmentResponse.Status.MISSING_GEOMETRY,
                    "A valid mission Polygon and saved route LineString are both required.", checkedAt, null, null, null);
        if (!MissionGeometryCodec.validForContainment(area.getGeometry()) || !MissionGeometryCodec.validForContainment(route.getRoute()))
            return containment(missionId, routeId, RouteContainmentResponse.Status.INVALID_GEOMETRY,
                    "The stored Polygon or route geometry is invalid for WGS84 spatial checking.", checkedAt, null, null, null);
        try {
            MissionRouteSpatialRepository.SpatialResult result = routeSpatialRepository.checkContainment(missionId, routeId);
            JsonNode outside = result.outsideGeoJson() == null ? null : objectMapper.readTree(result.outsideGeoJson());
            RouteContainmentResponse.Status status = result.covered()
                    ? RouteContainmentResponse.Status.WITHIN_OR_ON_BOUNDARY
                    : result.intersects() ? RouteContainmentResponse.Status.PARTLY_OUTSIDE : RouteContainmentResponse.Status.OUTSIDE;
            String message = switch (status) {
                case WITHIN_OR_ON_BOUNDARY -> "The complete saved route is within or on the mission Polygon boundary.";
                case PARTLY_OUTSIDE -> "Part of the saved route lies outside the mission Polygon; outside linework is shown.";
                default -> "The saved route lies outside the mission Polygon; outside linework is shown.";
            };
            return containment(missionId, routeId, status, message, checkedAt, result.missionGeometryHash(), result.routeGeometryHash(), outside);
        } catch (DataAccessException | java.io.IOException exception) {
            return containment(missionId, routeId, RouteContainmentResponse.Status.UNAVAILABLE,
                    "The spatial containment query could not be completed. Retry when the Mission Service database is available.",
                    checkedAt, null, null, null);
        }
    }

    private Mission requireMissionArea(UUID id) {
        Mission mission = missionRepository.findById(id).orElseThrow(() -> new MissionNotFoundException(id));
        if (mission.getGeometry() == null) throw new MissionGeometryRequiredException();
        return mission;
    }

    private Mission requireRoute(UUID id) {
        Mission route = missionRepository.findById(id).orElseThrow(() -> new MissionNotFoundException(id));
        if (route.getRoute() == null || route.getGeometry() != null)
            throw new RouteAssociationException("The selected saved record is not a LineString route.");
        return route;
    }

    private org.locationtech.jts.geom.LineString requireRouteRequest(MissionRequest request) {
        if (request.geometry() != null && !request.geometry().isNull())
            throw new InvalidMissionGeometryException("A route record cannot replace its route with a Polygon.");
        if (request.route() == null || request.route().isNull())
            throw new InvalidMissionGeometryException("A saved route LineString is required.");
        return MissionGeometryCodec.parseRoute(request.route());
    }

    private static RouteContainmentResponse containment(UUID missionId, UUID routeId, RouteContainmentResponse.Status status,
            String message, Instant checkedAt, String missionHash, String routeHash, JsonNode outside) {
        return new RouteContainmentResponse(missionId, routeId, status,
                "Boundary-touching route segments count as contained (PostGIS ST_Covers).", message, checkedAt,
                missionHash, routeHash, outside);
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
