import type { PolygonGeometry, Position } from './geometry'

/** The current boundary editor supports one exterior ring; preserve hole rings unchanged. */
export function editablePolygonBoundary(geometry: PolygonGeometry): Position[] | undefined {
  if (geometry.type !== 'Polygon' || geometry.coordinates.length !== 1) return undefined
  const ring = geometry.coordinates[0] ?? []
  const closed = ring.length > 1 && ring[0][0] === ring.at(-1)![0] && ring[0][1] === ring.at(-1)![1]
  return (closed ? ring.slice(0, -1) : ring).map(([longitude, latitude]) => [longitude, latitude])
}
