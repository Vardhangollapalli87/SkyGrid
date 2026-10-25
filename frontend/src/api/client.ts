import type { PolygonGeometry } from '../domain/geometry'

export type Mission = { id: string; name: string; description: string | null; createdAt: string; geometry?: PolygonGeometry | null }
export type MissionPage = { content: Mission[]; page: number; size: number; totalElements: number; totalPages: number }
export type ZoneCheck = { missionId: string; intersects: boolean; message: string; dataNotice: string; zones: { id: string; name: string; demonstration: boolean }[] }
export class ApiError extends Error {
  constructor(message: string, readonly kind: 'validation' | 'network' | 'timeout' | 'server' | 'not-found', readonly status?: number) { super(message); this.name = 'ApiError' }
}
const base = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
const timeoutMs = 12_000

async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs)
  try {
    let response: Response
    const signal = init.signal ? AbortSignal.any([controller.signal, init.signal]) : controller.signal
    try { response = await fetch(`${base}${path}`, { ...init, signal, headers: { Accept: 'application/json', ...init.headers } }) }
    catch (e) {
      if (init.signal?.aborted) throw new ApiError('Request was cancelled.', 'network')
      if (retry && init.method !== 'POST') { await delay(900); return request(path, init, false) }
      if (e instanceof DOMException && e.name === 'AbortError') throw new ApiError('The mission service took too long to respond. It may be starting after an idle period.', 'timeout')
      throw new ApiError('Could not reach the Mission Service. Check the connection and try again.', 'network')
    }
    if (!response.ok) {
      const problem = await response.json().catch(() => ({})) as { title?: string; detail?: string; errors?: { field: string; message: string }[] }
      const detail = problem.errors?.map(x => `${x.field}: ${x.message}`).join(' ') || problem.detail || problem.title || `Request failed (${response.status}).`
      throw new ApiError(detail, response.status === 400 ? 'validation' : response.status === 404 ? 'not-found' : response.status >= 500 ? 'server' : 'validation', response.status)
    }
    return await response.json() as T
  } finally { window.clearTimeout(timeout) }
}
const delay = (ms: number) => new Promise(resolve => window.setTimeout(resolve, ms))

export const missionApi = {
  list: (page = 0, size = 20, signal?: AbortSignal) => request<MissionPage>(`/api/v1/missions?page=${page}&size=${size}`, { signal }),
  create: (input: { name: string; description?: string; geometry: PolygonGeometry }) => request<Mission>('/api/v1/missions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) }, false),
  get: (id: string, signal?: AbortSignal) => request<Mission>(`/api/v1/missions/${encodeURIComponent(id)}`, { signal }),
  check: (id: string, signal?: AbortSignal) => request<ZoneCheck>(`/api/v1/missions/${encodeURIComponent(id)}/restricted-zone-check`, { signal }),
}
