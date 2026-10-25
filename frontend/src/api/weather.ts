export type WeatherCoordinate = { longitude: number; latitude: number }
export type WeatherForecast = { validAt: string; temperatureC: number | null; windSpeedMs: number | null; windGustMs: number | null; precipitationMm: number | null }
export type WeatherAssessment = {
  assessmentId: string
  status: 'COMPLETE' | 'PARTIAL'
  provider: string
  retrievedAt: string
  timezone: string
  forecastHours: number
  strategy: string
  thresholds: { windSpeedMs: number; windGustMs: number; precipitationMmPrecedingHour: number }
  samples: { sampleIndex: number; coordinate: WeatherCoordinate; forecast: WeatherForecast[] }[]
  variation: { field: string; unit: string; minimum: number | null; maximum: number | null; range: number | null; sampleMinimum: number | null; sampleMaximum: number | null; sampleRange: number | null }[]
  warnings: { code: string; field: string; threshold: number; unit: string; measuredValue: number; validAt: string; coordinate: WeatherCoordinate; sampleIndex: number; explanation: string }[]
  limitations: string[]
}

export type WeatherPanelState =
  | { status: 'idle' }
  | { status: 'loading'; assessment?: WeatherAssessment }
  | { status: 'success' | 'partial'; assessment: WeatherAssessment }
  | { status: 'unavailable' | 'validation-error'; error: string; assessment?: WeatherAssessment }
export type WeatherPanelAction = { type: 'reset' } | { type: 'loading' } | { type: 'result'; assessment: WeatherAssessment } | { type: 'error'; error: string; validation: boolean }

export function weatherPanelReducer(state: WeatherPanelState, action: WeatherPanelAction): WeatherPanelState {
  if (action.type === 'reset') return { status: 'idle' }
  if (action.type === 'loading') return { status: 'loading', ...('assessment' in state && state.assessment ? { assessment: state.assessment } : {}) }
  if (action.type === 'result') return { status: action.assessment.status === 'PARTIAL' ? 'partial' : 'success', assessment: action.assessment }
  return { status: action.validation ? 'validation-error' : 'unavailable', error: action.error,
    ...('assessment' in state && state.assessment ? { assessment: state.assessment } : {}) }
}
export function weatherAssessmentIsStale(state: WeatherPanelState) { return (state.status === 'loading' || state.status === 'unavailable' || state.status === 'validation-error') && Boolean(state.assessment) }

const base = (import.meta.env.VITE_HAZARD_API_BASE_URL || '/hazard-api').replace(/\/$/, '')
export class WeatherApiError extends Error {
  constructor(message: string, readonly kind: 'validation' | 'provider' | 'network' | 'timeout', readonly status?: number) { super(message); this.name = 'WeatherApiError' }
}
export const weatherApi = {
  assess: async (input: { assessmentType: 'POINT' | 'POLYGON' | 'ROUTE'; geometry: GeoJSON.Point | GeoJSON.Polygon | GeoJSON.LineString; forecastHours?: number }, signal?: AbortSignal) => {
    let response: Response
    try { response = await fetch(`${base}/api/v1/hazards/weather-assessment`, { method: 'POST', signal,
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' }, body: JSON.stringify(input) }) }
    catch (cause) {
      if (signal?.aborted) throw new WeatherApiError('Weather assessment was cancelled.', 'network')
      if (cause instanceof DOMException && cause.name === 'AbortError') throw new WeatherApiError('Weather service request timed out.', 'timeout')
      throw new WeatherApiError('Could not reach the Hazard Service.', 'network')
    }
    if (!response.ok) {
      const problem = await response.json().catch(() => ({})) as { title?: string; detail?: string }
      throw new WeatherApiError(problem.detail || problem.title || `Weather request failed (${response.status}).`, response.status === 400 || response.status === 413 ? 'validation' : 'provider', response.status)
    }
    try { return await response.json() as WeatherAssessment }
    catch { throw new WeatherApiError('Hazard Service returned an unreadable assessment.', 'provider', response.status) }
  },
}
