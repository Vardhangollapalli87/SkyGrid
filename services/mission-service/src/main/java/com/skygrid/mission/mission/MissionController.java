package com.skygrid.mission.mission;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/{id}")
    public MissionResponse getMission(@PathVariable UUID id) {
        return missionService.getMission(id);
    }

    @GetMapping("/{id}/restricted-zone-check")
    public RestrictedZoneCheckResponse checkRestrictedZones(@PathVariable UUID id) {
        return missionService.checkRestrictedZones(id);
    }
}
