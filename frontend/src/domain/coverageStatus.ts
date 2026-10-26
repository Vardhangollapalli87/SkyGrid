import type { AirspaceDataset } from '../api/client'

export type CoverageState = 'loading' | 'ready' | 'unavailable'

export function coverageIndicator(state: CoverageState, datasets: AirspaceDataset[]) {
  if (state === 'loading') return 'Coverage loading'
  if (state === 'unavailable') return 'Airspace data unavailable'
  if (!datasets.length) return 'No airspace inventory'
  if (datasets.some(d => ['IN_PROGRESS', 'PARTIAL', 'FAILED'].includes(d.status) || !d.coverageVerified)) return 'Partial source collection'
  return 'Coverage unverified'
}
