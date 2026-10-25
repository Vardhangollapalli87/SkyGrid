// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, missionApi } from './client'

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
})
