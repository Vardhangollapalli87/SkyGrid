import { describe, expect, it } from 'vitest'
import { missionGeoJson } from './geoJsonExport'
import { measurePolygon } from './missionMetrics'
import type { Mission } from '../api/client'

const mission: Mission = {
  id: 'mission-1', name: 'Survey', description: 'Bridge inspection', createdAt: '2026-10-25T10:00:00Z',
  geometry: { type: 'Polygon', coordinates: [[[77.59, 12.97], [77.60, 12.97], [77.60, 12.98], [77.59, 12.98], [77.59, 12.97]]] },
}

describe('mission operations geometry', () => {
  it('measures WGS84 polygon geodesically in metres and square metres', () => {
    const measurements = measurePolygon(mission.geometry!)
    expect(measurements.perimeterM).toBeGreaterThan(4000)
    expect(measurements.perimeterM).toBeLessThan(5000)
    expect(measurements.areaM2).toBeGreaterThan(1_000_000)
    expect(measurements.areaM2).toBeLessThan(1_500_000)
  })

  it('exports valid GeoJSON with longitude/latitude and only supported mission properties', () => {
    const feature = missionGeoJson(mission)
    expect(feature.type).toBe('Feature')
    expect(feature.geometry.coordinates[0][0]).toEqual([77.59, 12.97])
    expect(feature.geometry.coordinates[0][0]).toEqual(feature.geometry.coordinates[0].at(-1))
    expect(feature.properties).toEqual({ id: mission.id, name: mission.name, description: mission.description, createdAt: mission.createdAt })
    expect(Object.keys(feature.properties).sort()).toEqual(['createdAt', 'description', 'id', 'name'])
  })

  it('refuses malformed saved polygons', () => {
    expect(() => missionGeoJson({ ...mission, geometry: { type: 'Polygon', coordinates: [[[0, 0], [2, 2], [0, 2], [2, 0], [0, 0]]] } })).toThrow(/invalid/i)
  })
})
