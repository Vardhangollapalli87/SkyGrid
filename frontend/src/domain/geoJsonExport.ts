import { validatePolygon, type PolygonGeometry, type Position } from './geometry'
import type { Mission } from '../api/client'

export type MissionGeoJsonFeature = {
  type: 'Feature'
  geometry: PolygonGeometry
  properties: { id: string; name: string; description: string | null; createdAt: string }
}

export function missionGeoJson(mission: Mission): MissionGeoJsonFeature {
  const geometry = mission.geometry
  if (!geometry || geometry.type !== 'Polygon' || geometry.coordinates.length !== 1) {
    throw new Error('Export is available only for saved missions with a single-ring polygon.')
  }
  const ring = geometry.coordinates[0]
  if (!ring || validatePolygon(ring.slice(0, -1)).length > 0 || !same(ring[0], ring.at(-1)!)) {
    throw new Error('The saved polygon is invalid or its boundary is not closed; GeoJSON export is unavailable.')
  }
  return {
    type: 'Feature',
    geometry: { type: 'Polygon', coordinates: [ring.map(([longitude, latitude]): Position => [longitude, latitude]) ] },
    properties: { id: mission.id, name: mission.name, description: mission.description, createdAt: mission.createdAt },
  }
}

function same(a: readonly number[], b: readonly number[]) { return a[0] === b[0] && a[1] === b[1] }
