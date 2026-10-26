package com.skygrid.mission.mission;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/missions")
public class MissionController {

    private final MissionService missionService;

    public MissionController(MissionService missionService) {
        this.missionService = missionService;
    }

    @GetMapping
    public MissionPageResponse listMissions(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidPaginationException();
        }
        return MissionPageResponse.from(missionService.listMissions(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PostMapping
    public ResponseEntity<MissionResponse> createMission(@Valid @RequestBody MissionRequest request) {
        MissionResponse created = missionService.createMission(request);
        return ResponseEntity.created(URI.create("/api/v1/missions/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public MissionResponse updateMission(@PathVariable UUID id, @Valid @RequestBody MissionRequest request) {
        return missionService.updateMission(id, request);
    }

    @GetMapping("/{id}")
    public MissionResponse getMission(@PathVariable UUID id) {
        return missionService.getMission(id);
    }

    @GetMapping("/{id}/routes")
    public java.util.List<MissionResponse> listRoutes(@PathVariable UUID id) {
        return missionService.listAssociatedRoutes(id);
    }

    @PostMapping("/{id}/routes")
    public ResponseEntity<MissionResponse> createRoute(@PathVariable UUID id, @Valid @RequestBody MissionRequest request) {
        MissionResponse created = missionService.createAssociatedRoute(id, request);
        return ResponseEntity.created(URI.create("/api/v1/missions/" + id + "/routes/" + created.id())).body(created);
    }

    @PutMapping("/{id}/routes/{routeId}")
    public MissionResponse updateRoute(@PathVariable UUID id, @PathVariable UUID routeId,
                                       @Valid @RequestBody MissionRequest request) {
        return missionService.updateAssociatedRoute(id, routeId, request);
    }

    @DeleteMapping("/{id}/routes/{routeId}")
    public MissionResponse detachAndClearRoute(@PathVariable UUID id, @PathVariable UUID routeId) {
        return missionService.clearAssociatedRoute(id, routeId);
    }

    @GetMapping("/{id}/routes/{routeId}/containment")
    public RouteContainmentResponse checkRouteContainment(@PathVariable UUID id, @PathVariable UUID routeId) {
        return missionService.checkRouteContainment(id, routeId);
    }

    @GetMapping("/{id}/restricted-zone-check")
    public RestrictedZoneCheckResponse checkRestrictedZones(@PathVariable UUID id) {
        return missionService.checkRestrictedZones(id);
    }
}
