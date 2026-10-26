// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, missionApi } from './client'
import type { PolygonGeometry } from '../domain/geometry'

describe('Mission API client', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn()))
  it('maps backend validation problems to typed errors', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ title: 'Validation failed', detail: 'Name is required.' }), { status: 400 }))
    await expect(missionApi.list()).rejects.toMatchObject({ kind: 'validation', status: 400 })
  })
  it('does not retry creation after failure', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('offline'))
    await expect(missionApi.create({ name: 'A', geometry: { type: 'Polygon', coordinates: [] } })).rejects.toBeInstanceOf(ApiError)
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('requests airspace features scoped to the viewport', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ type: 'FeatureCollection', features: [] }), { status: 200 }))
    await missionApi.airspace({ west: -2, south: 50, east: 1, north: 53 })
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/api/v1/airspace/features?west=-2&south=50&east=1&north=53'), expect.any(Object))
  })
  it('persists ordered LineString routes through the existing mission resource', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ id: 'm1' }), { status: 200 }))
    await missionApi.update('m1', { name: 'Route', route: { type: 'LineString', coordinates: [[77.59, 12.97], [77.60, 12.97]] } })
    expect(fetch).toHaveBeenCalledWith('/api/v1/missions/m1', expect.objectContaining({ method: 'PUT' }))
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0][1]?.body))).toEqual({ name: 'Route', route: { type: 'LineString', coordinates: [[77.59, 12.97], [77.60, 12.97]] } })
  })
  it('creates and reopens a six-vertex Polygon without changing geometry semantics', async () => {
    const geometry: PolygonGeometry = { type: 'Polygon', coordinates: [[[79.124,18.444],[79.130,18.444],[79.134,18.440],[79.132,18.434],[79.126,18.432],[79.122,18.437],[79.124,18.444]]] }
    const saved = { id: 'polygon-karimnagar', name: 'Area', description: null, createdAt: '2026-10-26T00:00:00Z', geometry }
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify(saved), { status: 201 })).mockResolvedValueOnce(new Response(JSON.stringify(saved), { status: 200 }))
    const created = await missionApi.create({ name: saved.name, geometry })
    const reopened = await missionApi.get(created.id)
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0][1]?.body))).toEqual({ name: 'Area', geometry })
    expect(reopened.geometry).toEqual(geometry)
    expect(reopened.route).toBeUndefined()
  })
  it('updates a saved Polygon through PUT without converting it to a route', async () => {
    const geometry: PolygonGeometry = { type: 'Polygon', coordinates: [[[79.124,18.444],[79.130,18.444],[79.134,18.440],[79.132,18.434],[79.126,18.432],[79.122,18.437],[79.124,18.444]]] }
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ id: 'polygon-existing', name: 'Edited area', geometry }), { status: 200 }))
    const updated = await missionApi.update('polygon-existing', { name: 'Edited area', geometry })
    expect(fetch).toHaveBeenCalledWith('/api/v1/missions/polygon-existing', expect.objectContaining({ method: 'PUT' }))
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0][1]?.body))).toEqual({ name: 'Edited area', geometry })
    expect(updated.geometry?.type).toBe('Polygon')
    expect(updated.route).toBeUndefined()
  })
  it('creates and reopens a LineString route separately from Polygon geometry', async () => {
    const route = { type: 'LineString' as const, coordinates: [[79.16473,18.45225],[79.17035,18.44731],[79.17612,18.44107],[79.18144,18.43562],[79.18703,18.42988]] as [number, number][] }
    const saved = { id: 'route-karimnagar', name: 'Track', description: null, createdAt: '2026-10-26T00:00:00Z', route }
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify(saved), { status: 201 })).mockResolvedValueOnce(new Response(JSON.stringify(saved), { status: 200 }))
    const created = await missionApi.create({ name: saved.name, route })
    const reopened = await missionApi.get(created.id)
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0][1]?.body))).toEqual({ name: 'Track', route })
    expect(reopened.route).toEqual(route)
    expect(reopened.geometry).toBeUndefined()
  })
})
