import { describe, expect, it } from 'vitest'
import { coverageIndicator } from './coverageStatus'
import type { AirspaceDataset } from '../api/client'

const dataset: AirspaceDataset = {
  country: 'USA', source: 'FAA', dataset: 'restricted', category: 'RESTRICTION', spatialFeatures: 1600,
  unconvertedRecords: 0, inputCount: 2287, rejectedCount: 3, duplicateCount: 0, insertedCount: 1600,
  updatedCount: 0, skippedCount: 0, nonSpatialCount: 0, failedCount: 0, lastImportAt: null,
  lastAttemptAt: '2026-10-26T00:00:00Z', coverageVerified: false, limitations: 'Collection is partial.',
  status: 'IN_PROGRESS', sourceUrl: null, sourceEffectiveAt: null, freshnessStatus: 'NOT_EVALUATED',
}

describe('airspace coverage status summary', () => {
  it('keeps loading, unavailable, and partial inventory states distinct', () => {
    expect(coverageIndicator('loading', [])).toBe('Coverage loading')
    expect(coverageIndicator('unavailable', [dataset])).toBe('Airspace data unavailable')
    expect(coverageIndicator('ready', [dataset])).toBe('Partial source collection')
    expect(coverageIndicator('ready', [])).toBe('No airspace inventory')
  })
})
