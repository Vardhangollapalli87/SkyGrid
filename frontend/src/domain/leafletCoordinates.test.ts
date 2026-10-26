// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import L from 'leaflet'
import { fromLeafletLatLng } from './leafletCoordinates'
import { closeRing, toGeoJson, validatePolygon } from './geometry'
import { missionGeoJson } from './geoJsonExport'
import type { Mission } from '../api/client'

const karimnagarRing: [number, number][] = [
  [79.124, 18.444], [79.130, 18.444], [79.134, 18.440],
  [79.132, 18.434], [79.126, 18.432], [79.122, 18.437],
]

describe('Leaflet to WGS84 coordinate boundary', () => {
  it('normalizes a wrapped world-copy longitude without swapping axes', () => {
    const leafletPoint = L.latLng(18.45225, 439.16473)
    expect(leafletPoint.lng).toBe(439.16473)
    const wrapped = fromLeafletLatLng(leafletPoint)
    expect(wrapped[0]).toBeCloseTo(79.16473, 10)
    expect(wrapped[1]).toBe(18.45225)
    expect(fromLeafletLatLng({ lat: 18.45225, lng: 79.16473 })).toEqual([79.16473, 18.45225])
    expect(fromLeafletLatLng({ lat: 18, lng: 180 })).toEqual([180, 18])
  })

  it('builds and exports a six-vertex Karimnagar polygon in GeoJSON order', () => {
    const positions = karimnagarRing.map(([longitude, latitude]) => fromLeafletLatLng({ lat: latitude, lng: longitude }))
    expect(validatePolygon(positions)).toEqual([])
    const geometry = toGeoJson(positions)
    expect(geometry.coordinates[0]).toEqual(closeRing(karimnagarRing))
    const mission: Mission = { id: 'karimnagar-test', name: 'Area regression', description: null, createdAt: '2026-10-26T00:00:00Z', geometry }
    expect(missionGeoJson(mission).geometry.coordinates[0][0]).toEqual([79.124, 18.444])
  })

  it('leaves genuinely invalid latitude and non-finite longitude visible to validation', () => {
    const invalidLatitude = fromLeafletLatLng({ lat: 91, lng: 79 })
    const invalidLongitude = fromLeafletLatLng({ lat: 18, lng: Number.POSITIVE_INFINITY })
    expect(validatePolygon([invalidLatitude, [80, 18], [79, 19]])).toContain('Coordinates must be longitude/latitude within WGS84 bounds.')
    expect(validatePolygon([invalidLongitude, [80, 18], [79, 19]])).toContain('Coordinates must be longitude/latitude within WGS84 bounds.')
  })
})
