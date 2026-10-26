package com.skygrid.mission.mission;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {
    private final MissionService missionService;

    public RouteController(MissionService missionService) { this.missionService = missionService; }

    @GetMapping
    public List<MissionResponse> listStandaloneRoutes() { return missionService.listStandaloneRoutes(); }

    @PutMapping("/{routeId}/association/{missionId}")
    public MissionResponse associate(@PathVariable UUID routeId, @PathVariable UUID missionId) {
        return missionService.associateRoute(missionId, routeId);
    }

    @DeleteMapping("/{routeId}/association")
    public MissionResponse disassociate(@PathVariable UUID routeId) { return missionService.disassociateRoute(routeId); }
}
