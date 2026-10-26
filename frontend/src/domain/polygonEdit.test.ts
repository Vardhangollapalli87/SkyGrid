import { describe, expect, it } from 'vitest'
import { editablePolygonBoundary } from './polygonEdit'
import type { PolygonGeometry } from './geometry'

describe('saved Polygon editing compatibility', () => {
  it('opens a single-ring polygon without its closing duplicate', () => {
    const polygon: PolygonGeometry = { type: 'Polygon', coordinates: [[[79.1,18.4],[79.2,18.4],[79.2,18.5],[79.1,18.4]]] }
    expect(editablePolygonBoundary(polygon)).toEqual([[79.1,18.4],[79.2,18.4],[79.2,18.5]])
  })
  it('does not flatten or discard interior rings in existing Polygon missions', () => {
    const polygon: PolygonGeometry = { type: 'Polygon', coordinates: [
      [[79.1,18.4],[79.2,18.4],[79.2,18.5],[79.1,18.4]],
      [[79.12,18.42],[79.13,18.42],[79.13,18.43],[79.12,18.42]],
    ] }
    expect(editablePolygonBoundary(polygon)).toBeUndefined()
    expect(polygon.coordinates).toHaveLength(2)
  })
})
