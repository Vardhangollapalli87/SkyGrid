import { validatePolygon, type PolygonGeometry, type Position } from './geometry'

const EARTH_RADIUS_METERS = 6_371_008.8

/** Spherical geodesic measurements for a WGS84 polygon; coordinates remain [longitude, latitude]. */
export function measurePolygon(geometry: PolygonGeometry): { areaM2: number; perimeterM: number } {
  const ring = geometry.coordinates[0]
  if (validatePolygon(ring.slice(0, -1)).length > 0) throw new Error('Mission polygon is invalid.')
  if (geometry.coordinates.length !== 1) throw new Error('Mission polygon must contain one exterior ring.')

  let perimeter = 0
  let areaAccumulator = 0
  for (let i = 0; i < ring.length - 1; i++) {
    const [lng1, lat1] = ring[i]
    const [lng2, lat2] = ring[i + 1]
    perimeter += distanceMeters(ring[i], ring[i + 1])
    const lon1 = radians(lng1)
    const lon2 = radians(lng2)
    areaAccumulator += (lon2 - lon1) * (2 + Math.sin(radians(lat1)) + Math.sin(radians(lat2)))
  }
  const sphereArea = 4 * Math.PI * EARTH_RADIUS_METERS ** 2
  let area = Math.abs(areaAccumulator) * EARTH_RADIUS_METERS ** 2 / 2
  area = Math.min(area, sphereArea - area)
  return { areaM2: area, perimeterM: perimeter }
}

function distanceMeters(a: Position, b: Position): number {
  const lat1 = radians(a[1]), lat2 = radians(b[1])
  const dLat = lat2 - lat1, dLon = radians(b[0] - a[0])
  const haversine = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1, haversine)))
}

const radians = (degrees: number) => degrees * Math.PI / 180
