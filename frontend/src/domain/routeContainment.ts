import type { Mission, RouteContainment } from '../api/client'

export type ContainmentSnapshot = { result: RouteContainment; missionGeometry: string; routeGeometry: string }

export function containmentIsCurrent(snapshot: ContainmentSnapshot | undefined, mission: Mission | undefined, route: Mission | undefined) {
  return Boolean(snapshot && mission?.geometry && route?.route
    && snapshot.result.missionId === mission.id && snapshot.result.routeId === route.id
    && snapshot.missionGeometry === JSON.stringify(mission.geometry)
    && snapshot.routeGeometry === JSON.stringify(route.route))
}
