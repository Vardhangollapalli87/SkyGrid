export type Position = [number, number]
export type PolygonGeometry = { type: 'Polygon'; coordinates: Position[][] }

export function closeRing(points: Position[]): Position[] {
  if (points.length < 3) return points.map(([lng, lat]) => [lng, lat])
  const open = points.slice(0, -1)
  const last = points.at(-1)!
  if (points.length > 3 && same(points[0], last)) return [...open, [...points[0]]]
  return [...points.map(([lng, lat]) => [lng, lat] as Position), [...points[0]]]
}

export function validatePolygon(points: Position[]): string[] {
  const errors: string[] = []
  if (points.length < 3) return ['Add at least three vertices to draw a mission area.']
  if (points.some(([lng, lat]) => !Number.isFinite(lng) || !Number.isFinite(lat) || lng < -180 || lng > 180 || lat < -90 || lat > 90)) errors.push('Coordinates must be longitude/latitude within WGS84 bounds.')
  if (points.some((point, index) => index > 0 && same(point, points[index - 1]))) errors.push('Remove consecutive duplicate vertices.')
  const ring = closeRing(points)
  const distinct = new Set(ring.slice(0, -1).map(([lng, lat]) => `${lng},${lat}`))
  if (distinct.size < 3) errors.push('A polygon needs at least three distinct vertices.')
  if (selfIntersects(ring)) errors.push('This polygon crosses itself. Adjust the vertices before saving.')
  if (Math.abs(area(ring)) < 1e-12) errors.push('The polygon area is too small or collapsed.')
  return [...new Set(errors)]
}

export function toGeoJson(points: Position[]): PolygonGeometry {
  return { type: 'Polygon', coordinates: [closeRing(points)] }
}

function same(a: Position, b: Position) { return a[0] === b[0] && a[1] === b[1] }
function area(points: Position[]) { return points.slice(0, -1).reduce((sum, p, i) => sum + p[0] * points[i + 1][1] - points[i + 1][0] * p[1], 0) / 2 }
function selfIntersects(ring: Position[]) {
  const n = ring.length - 1
  for (let i = 0; i < n; i++) for (let j = i + 1; j < n; j++) {
    if (j === i || j === i + 1 || (i === 0 && j === n - 1)) continue
    if (intersects(ring[i], ring[i + 1], ring[j], ring[j + 1])) return true
  }
  return false
}
function intersects(a: Position, b: Position, c: Position, d: Position) {
  const cross = (p: Position, q: Position, r: Position) => (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])
  const epsilon = 1e-12
  const x1 = cross(a, b, c), x2 = cross(a, b, d), x3 = cross(c, d, a), x4 = cross(c, d, b)
  const onSegment = (p: Position, q: Position, r: Position) => q[0] >= Math.min(p[0], r[0]) - epsilon && q[0] <= Math.max(p[0], r[0]) + epsilon && q[1] >= Math.min(p[1], r[1]) - epsilon && q[1] <= Math.max(p[1], r[1]) + epsilon
  if ((x1 > epsilon && x2 < -epsilon || x1 < -epsilon && x2 > epsilon) && (x3 > epsilon && x4 < -epsilon || x3 < -epsilon && x4 > epsilon)) return true
  return Math.abs(x1) <= epsilon && onSegment(a, c, b) || Math.abs(x2) <= epsilon && onSegment(a, d, b) || Math.abs(x3) <= epsilon && onSegment(c, a, d) || Math.abs(x4) <= epsilon && onSegment(c, b, d)
}
