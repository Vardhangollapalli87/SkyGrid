import { describe, expect, it } from 'vitest'
import { closeRing, toGeoJson, validatePolygon } from './geometry'

const triangle: [number, number][] = [[77.59, 12.97], [77.60, 12.97], [77.60, 12.98]]
describe('mission polygon geometry', () => {
  it('closes a ring in longitude-latitude order and builds GeoJSON', () => {
    expect(closeRing(triangle)).toEqual([...triangle, triangle[0]])
    expect(toGeoJson(triangle)).toEqual({ type: 'Polygon', coordinates: [[...triangle, triangle[0]]] })
    expect(validatePolygon(triangle)).toEqual([])
  })
  it('rejects too few vertices, bounds, duplicate edges, and self intersections', () => {
    expect(validatePolygon(triangle.slice(0, 2))).toHaveLength(1)
    expect(validatePolygon([[181, 0], [1, 0], [1, 1]])).toContain('Coordinates must be longitude/latitude within WGS84 bounds.')
    expect(validatePolygon([[0, 0], [1, 0], [1, 0], [0, 1]])).toContain('Remove consecutive duplicate vertices.')
    expect(validatePolygon([[0, 0], [2, 2], [0, 2], [2, 0]])).toContain('This polygon crosses itself. Adjust the vertices before saving.')
    expect(validatePolygon([[0, 0], [2, 0], [1, 0], [1, 2]])).toContain('This polygon crosses itself. Adjust the vertices before saving.')
  })
})
