import type { RouteGeometry } from '../api/client'
import type { Position } from './geometry'

export function validateRoute(points: Position[]): string[] {
  if (points.length < 2) return ['Add at least two waypoints to create a route.']
  const errors: string[] = []
  if (points.some(([longitude, latitude]) => !Number.isFinite(longitude) || !Number.isFinite(latitude)
      || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90))
    errors.push('Coordinates must be finite longitude/latitude values within WGS84 bounds.')
  if (points.some((point, index) => index > 0 && same(point, points[index - 1])))
    errors.push('Consecutive waypoints must not be identical.')
  return errors
}

export function toRouteGeometry(points: Position[]): RouteGeometry {
  const errors = validateRoute(points)
  if (errors.length) throw new Error(errors.join(' '))
  return { type: 'LineString', coordinates: points.map(([longitude, latitude]) => [longitude, latitude]) }
}

export function estimatedGroundTrackMeters(points: Position[]) {
  return points.slice(1).reduce((total, point, index) => total + distanceMeters(points[index], point), 0)
}

const R = 6_371_008.8
function distanceMeters(a: Position, b: Position) {
  const rad = (degree: number) => degree * Math.PI / 180
  const lat1 = rad(a[1]), lat2 = rad(b[1]), dLat = lat2 - lat1, dLon = rad(b[0] - a[0])
  const haversine = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(Math.min(1, haversine)))
}
const same = (a: Position, b: Position) => a[0] === b[0] && a[1] === b[1]
