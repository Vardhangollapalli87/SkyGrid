import { describe, expect, it } from 'vitest'
import { missionRouteGeoJson } from './routeGeoJson'
import { estimatedGroundTrackMeters, toRouteGeometry, validateRoute } from './route'
import type { Mission } from '../api/client'

const waypoints: [number, number][] = [[77.59, 12.97], [77.60, 12.97], [77.60, 12.98]]
const mission: Mission = { id: 'm-1', name: 'Route survey', description: null, createdAt: '2026-10-25T00:00:00Z', route: { type: 'LineString', coordinates: waypoints } }

describe('waypoint route', () => {
  it('validates ordered coordinates and estimates ground-track length in metres', () => {
    expect(validateRoute(waypoints)).toEqual([])
    expect(toRouteGeometry(waypoints)).toEqual({ type: 'LineString', coordinates: waypoints })
    expect(estimatedGroundTrackMeters(waypoints)).toBeGreaterThan(2000)
    expect(estimatedGroundTrackMeters(waypoints)).toBeLessThan(3000)
  })
  it('rejects insufficient, out-of-range, and duplicate consecutive waypoints', () => {
    expect(validateRoute([[0, 0]])).toContain('Add at least two waypoints to create a route.')
    expect(validateRoute([[181, 0], [0, 1]])[0]).toContain('WGS84 bounds')
    expect(validateRoute([[0, 0], [0, 0]])).toContain('Consecutive waypoints must not be identical.')
  })
  it('exports LineString coordinates in longitude/latitude order and supported properties only', () => {
    const feature = missionRouteGeoJson(mission)
    expect(feature.geometry).toEqual({ type: 'LineString', coordinates: waypoints })
    expect(feature.properties).toEqual({ id: 'm-1', name: 'Route survey', description: null, createdAt: mission.createdAt })
  })
})
