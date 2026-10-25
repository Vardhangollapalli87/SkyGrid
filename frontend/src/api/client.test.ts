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
})
