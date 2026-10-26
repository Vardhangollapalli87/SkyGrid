import type { Position } from './geometry'

/** Convert Leaflet's named latitude/longitude fields to GeoJSON order. Leaflet
 * can report longitudes outside WGS84 when a wrapped world copy is clicked. */
export function fromLeafletLatLng(latlng: { lat: number; lng: number }): Position {
  const { lat, lng } = latlng
  if (!Number.isFinite(lng)) return [lng, lat]
  if (lng >= -180 && lng <= 180) return [lng, lat]
  const wrapped = ((lng + 180) % 360 + 360) % 360 - 180
  return [wrapped === -180 && lng > 0 ? 180 : wrapped, lat]
}
