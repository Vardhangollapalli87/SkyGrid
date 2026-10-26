// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { weatherApi, weatherAssessmentIsStale, weatherPanelReducer, type WeatherAssessment } from './weather'

const result: WeatherAssessment = {
  assessmentId: 'a1', status: 'COMPLETE', provider: 'Open-Meteo Forecast API', retrievedAt: '2030-01-01T00:00:00Z',
  timezone: 'UTC', forecastHours: 24, strategy: 'route samples', thresholds: { windSpeedMs: 12, windGustMs: 17, precipitationMmPrecedingHour: 2 },
  samples: [], variation: [], warnings: [], limitations: [],
}
describe('weather assessment API and view state', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn()))
  afterEach(() => vi.unstubAllEnvs())
  it('keeps no-warning completion distinct from missing, partial, and stale data', () => {
    const success = weatherPanelReducer({ status: 'idle' }, { type: 'result', assessment: result })
    expect(success.status).toBe('success')
    expect(success.status === 'success' && success.assessment.warnings).toEqual([])
    const loading = weatherPanelReducer(success, { type: 'loading' })
    expect(loading.status).toBe('loading')
    expect(weatherAssessmentIsStale(loading)).toBe(true)
    const failed = weatherPanelReducer(loading, { type: 'error', error: 'Provider unavailable', validation: false })
    expect(failed.status).toBe('unavailable')
    expect(weatherAssessmentIsStale(failed)).toBe(true)
  })
  it('retains explicit provider warnings and exposes partial status', () => {
    const partial = { ...result, status: 'PARTIAL' as const, warnings: [{ code: 'WIND_GUST_HIGH', field: 'windGust', threshold: 17,
      unit: 'm/s', measuredValue: 19, validAt: '2030-01-01T01:00:00Z', coordinate: { longitude: 77, latitude: 12 },
      sampleIndex: 1, explanation: 'General planning indicator.' }] }
    const state = weatherPanelReducer({ status: 'idle' }, { type: 'result', assessment: partial })
    expect(state.status).toBe('partial')
    expect(state.status === 'partial' && state.assessment.warnings[0].measuredValue).toBe(19)
  })
  it('maps invalid request errors separately from provider failures', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ title: 'Invalid request', detail: 'Bad geometry' }), { status: 400 }))
    await expect(weatherApi.assess({ assessmentType: 'ROUTE', geometry: { type: 'LineString', coordinates: [[0, 0], [1, 1]] } }))
      .rejects.toMatchObject({ kind: 'validation', message: 'Bad geometry' })
    expect(weatherPanelReducer({ status: 'idle' }, { type: 'error', error: 'Bad geometry', validation: true }).status).toBe('validation-error')
  })
  it('sends geometry to the configured Hazard Service base', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify(result), { status: 200 }))
    await weatherApi.assess({ assessmentType: 'POLYGON', geometry: { type: 'Polygon', coordinates: [[[0,0],[1,0],[1,1],[0,0]]] } })
    expect(fetch).toHaveBeenCalledWith('/hazard-api/api/v1/hazards/weather-assessment', expect.objectContaining({ method: 'POST' }))
  })
  it('uses the shared public gateway base when configured', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://gateway.example')
    vi.resetModules()
    const { weatherApi: gatewayWeatherApi } = await import('./weather')
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify(result), { status: 200 }))

    await gatewayWeatherApi.assess({ assessmentType: 'POLYGON', geometry: { type: 'Polygon', coordinates: [[[0,0],[1,0],[1,1],[0,0]]] } })

    expect(fetch).toHaveBeenCalledWith('https://gateway.example/api/v1/hazards/weather-assessment', expect.objectContaining({ method: 'POST' }))
  })
})
