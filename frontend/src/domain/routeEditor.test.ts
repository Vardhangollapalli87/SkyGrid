import { describe, expect, it } from 'vitest'
import { fromLeafletLatLng } from './leafletCoordinates'
import { routeEditorReducer, createRouteEditorState } from './routeEditor'
import { toRouteGeometry, validateRoute } from './route'
import { missionRouteGeoJson } from './routeGeoJson'
import type { Mission } from '../api/client'
import type { PolygonGeometry } from './geometry'

const karimnagarWaypoints: [number, number][] = [
  [79.16473, 18.45225], [79.17035, 18.44731], [79.17612, 18.44107],
  [79.18144, 18.43562], [79.18703, 18.42988],
]
const leafletPoints = () => karimnagarWaypoints.map(([longitude, latitude]) => fromLeafletLatLng({ lat: latitude, lng: longitude }))

describe('route editor state and persistence payloads', () => {
  it('creates a valid ordered route with five Leaflet waypoints and RFC 7946 GeoJSON', () => {
    const points = leafletPoints()
    expect(validateRoute(points)).toEqual([])
    const route = toRouteGeometry(points)
    expect(route).toEqual({ type: 'LineString', coordinates: karimnagarWaypoints })
    const mission: Mission = { id: 'route-test', name: 'Karimnagar route', description: null, createdAt: '2026-10-26T00:00:00Z', route }
    expect(missionRouteGeoJson(mission).geometry).toEqual(route)
  })

  it('records waypoint drag, reorder, removal, and undoes the latest operation', () => {
    let state = createRouteEditorState(leafletPoints())
    const dragged = fromLeafletLatLng({ lat: 18.45010, lng: 439.16700 })
    state = routeEditorReducer(state, { type: 'replace', points: state.points.map((point, index) => index === 1 ? dragged : point) })
    expect(state.points[1][0]).toBeCloseTo(79.167, 10)
    expect(state.points[1][1]).toBe(18.4501)

    const reordered = [...state.points]
    ;[reordered[1], reordered[2]] = [reordered[2], reordered[1]]
    state = routeEditorReducer(state, { type: 'replace', points: reordered })
    expect(state.points[1]).toEqual(karimnagarWaypoints[2])
    expect(state.points[2][0]).toBeCloseTo(79.167, 10)
    expect(state.history.at(-1)?.[1][0]).toBeCloseTo(79.167, 10)

    state = routeEditorReducer(state, { type: 'replace', points: state.points.filter((_, index) => index !== 3) })
    expect(state.points).toHaveLength(4)
    const beforeUndo = state.history.at(-1)
    state = routeEditorReducer(state, { type: 'undo' })
    expect(state.points).toEqual(beforeUndo)
    expect(state.points).toHaveLength(5)
  })

  it('loads saved Polygon and LineString representations without changing their meanings', () => {
    const originalPolygon: PolygonGeometry = { type: 'Polygon', coordinates: [[[79.124,18.444],[79.130,18.444],[79.134,18.440],[79.132,18.434],[79.126,18.432],[79.122,18.437],[79.124,18.444]]] }
    const saved: Mission[] = [
      { id: 'polygon', name: 'Area', description: null, createdAt: '2026-10-26T00:00:00Z', geometry: originalPolygon },
      { id: 'route', name: 'Track', description: null, createdAt: '2026-10-26T00:00:00Z', route: toRouteGeometry(leafletPoints()) },
    ]
    const reopenedPolygon = { ...saved[0], geometry: structuredClone(saved[0].geometry) }
    const reopenedRoute = { ...saved[1], route: structuredClone(saved[1].route) }
    expect(reopenedPolygon.geometry).toEqual(originalPolygon)
    expect(reopenedPolygon.route).toBeUndefined()
    expect(reopenedRoute.route?.type).toBe('LineString')
    expect(reopenedRoute.geometry).toBeUndefined()
    expect(routeEditorReducer(createRouteEditorState(), { type: 'load', points: reopenedRoute.route!.coordinates }).points).toEqual(karimnagarWaypoints)
  })

  it('rejects genuinely out-of-bounds route coordinates', () => {
    expect(validateRoute([[181, 18.4], [79.2, 18.5]])).toContain('Coordinates must be finite longitude/latitude values within WGS84 bounds.')
  })
})
