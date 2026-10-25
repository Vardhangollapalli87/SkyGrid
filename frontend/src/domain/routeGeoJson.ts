import type { Mission, RouteGeometry } from '../api/client'
import { validateRoute } from './route'

export function missionRouteGeoJson(mission: Mission) {
  const route = mission.route
  if (!route || route.type !== 'LineString') throw new Error('Export is available only for a saved mission with a route.')
  const errors = validateRoute(route.coordinates)
  if (errors.length) throw new Error(`Saved route is invalid: ${errors.join(' ')}`)
  return {
    type: 'Feature' as const,
    geometry: { type: 'LineString' as const, coordinates: route.coordinates.map(([longitude, latitude]) => [longitude, latitude] as [number, number]) },
    properties: { id: mission.id, name: mission.name, description: mission.description, createdAt: mission.createdAt },
  }
}

export function asRouteGeometry(coordinates: [number, number][]): RouteGeometry { return { type: 'LineString', coordinates } }
