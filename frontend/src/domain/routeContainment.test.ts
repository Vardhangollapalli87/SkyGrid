import { describe, expect, it } from 'vitest'
import type { Mission, RouteContainment } from '../api/client'
import { containmentIsCurrent, type ContainmentSnapshot } from './routeContainment'

const area: Mission = { id: 'area-1', name: 'Area', description: null, createdAt: '2026-01-01T00:00:00Z', geometry: { type: 'Polygon', coordinates: [[[0,0],[2,0],[2,2],[0,2],[0,0]]] } }
const route: Mission = { id: 'route-1', name: 'Route', description: null, createdAt: '2026-01-01T00:00:00Z', associatedMissionId: area.id, route: { type: 'LineString', coordinates: [[.2,.2],[1,1]] } }
const result: RouteContainment = { missionId: area.id, routeId: route.id, status: 'WITHIN_OR_ON_BOUNDARY', boundarySemantics: 'ST_Covers', message: 'Contained', checkedAt: '2026-01-01T00:00:00Z', missionGeometryHash: 'a', routeGeometryHash: 'b' }
const snapshot: ContainmentSnapshot = { result, missionGeometry: JSON.stringify(area.geometry), routeGeometry: JSON.stringify(route.route) }

describe('route containment freshness', () => {
  it('keeps a response current only for the same area and route geometry', () => {
    expect(containmentIsCurrent(snapshot, area, route)).toBe(true)
    expect(containmentIsCurrent(snapshot, { ...area, id: 'other' }, route)).toBe(false)
    expect(containmentIsCurrent(snapshot, area, { ...route, id: 'other-route' })).toBe(false)
    expect(containmentIsCurrent(snapshot, { ...area, geometry: { ...area.geometry!, coordinates: [[[0,0],[3,0],[3,2],[0,2],[0,0]]] } }, route)).toBe(false)
    expect(containmentIsCurrent(snapshot, area, { ...route, route: { type: 'LineString', coordinates: [[.3,.2],[1,1]] } })).toBe(false)
  })
})
