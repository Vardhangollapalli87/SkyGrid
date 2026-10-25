# API contract

Implemented Mission Service routes are identified below. Remaining Hazard, assessment, and map routes are planned. JSON APIs use `/api/v1`, UUID IDs, UTC ISO-8601 times, GeoJSON longitude/latitude, validated input, and safe problem responses.

## Mission Service

| Method | Path | Purpose | Result |
|---|---|---|---|
| POST | `/api/v1/missions` | Create a mission with an optional Polygon area or LineString route | `201` |
| PUT | `/api/v1/missions/{missionId}` | Replace mission metadata and Polygon/LineString representation | `200` or `404` |
| GET | `/api/v1/missions` | Bounded paginated list | `200` |
| GET | `/api/v1/missions/{missionId}` | Retrieve mission, including geometry when present | `200` or `404` |
| GET | `/api/v1/missions/{missionId}/restricted-zone-check` | Check stored restricted-zone polygons with PostGIS `ST_Intersects` | `200`, `400` if geometry is absent, or `404` |
| GET | `/api/v1/missions/{missionId}/assessments` | Immutable history, newest first | `200` or `404` |
| GET | `/api/v1/demo-map` | Labeled restrictions and Mission hazard projection | `200` |
| GET | `/actuator/health` | Minimal safe health | `200`/`503` |

Mission list/get/create/update, restricted-zone check, and health routes are implemented. Assessment history and demo-map routes remain planned.

Airspace snapshot routes implemented in Mission Service:

| Method | Path | Purpose | Result |
|---|---|---|---|
| GET | `/api/v1/airspace/datasets` | Snapshot inventory with parsed, inserted, updated, skipped, spatial, non-spatial, rejected, failed, and attempt status/counts | `200` |
| GET | `/api/v1/airspace/features?west=&south=&east=&north=` | WGS84 viewport features, capped at 2,000; includes `truncated` when capped | GeoJSON `FeatureCollection` |

Only spatially validated features from `SUCCESS` or `PARTIAL` imports are returned by the viewport API. `IN_PROGRESS` and `FAILED` datasets are hidden so batch commits are not presented as a completed source. Coverage is marked unverified. Mission intersection checks include only compatible `RESTRICTION` records with valid geometry; unconverted records and other aviation information are excluded. See `docs/AIRSPACE_DATA_IMPORT.md` for snapshot schemas and exact limits.

Illustrative request:

```json
{"name":"Riverside survey","description":"Optional mission description","geometry":{"type":"Polygon","coordinates":[[[77.59,12.97],[77.60,12.97],[77.60,12.98],[77.59,12.98],[77.59,12.97]]]}}
```

`geometry` may be omitted to preserve the existing mission contract. When supplied, it must be a valid, closed, nonempty, two-dimensional GeoJSON `Polygon`; coordinate positions are `[longitude, latitude]` in WGS84/SRID 4326. Each coordinate must be within longitude `[-180,180]` and latitude `[-90,90]`. Unsupported types, malformed rings, invalid bounds, and invalid/self-intersecting topology return `400`. Mission responses include geometry when present and omit it for older missions with no geometry.

A mission may instead supply `route` as a GeoJSON `LineString` with at least two ordered WGS84 `[longitude, latitude]` positions; identical consecutive positions and out-of-range/non-numeric positions return `400`. `geometry` and `route` cannot both be supplied. The API stores route separately as PostGIS `geometry(LineString,4326)`. `PUT /api/v1/missions/{missionId}` is a full replacement of the mission's optional geometry representation and metadata; omitted `geometry`/`route` values clear those values. Existing Polygon clients and data are unchanged. Route distance shown in the UI is an approximate spherical geodesic ground-track length, not guaranteed flight distance. A route is not an airspace check, flight plan clearance, obstacle avoidance result, or flight duration estimate.

Restricted-zone check response example:

```json
{
  "missionId": "6a8adcb2-92cb-42fc-bd72-f638eb67e1d3",
  "intersects": true,
  "message": "Mission polygon intersects one or more stored restricted-zone polygons.",
  "dataNotice": "Restricted-zone data may be synthetic demonstration data and is not an official aviation source.",
  "zones": [
    {"id":"5cc6a65b-fb15-41ae-aab2-9567ac4c21d2","name":"Synthetic zone","demonstration":true}
  ]
}
```

If no stored zone intersects, `intersects` is `false` and `zones` is empty. A mission without geometry receives `400` with title `Mission geometry required`. The service does not seed restricted-zone records automatically.

The current check response does not indicate the total number of available restricted zones or whether the dataset is complete. A negative result means only that no stored zone returned by the query intersected; clients must not infer the dataset was populated or complete.

The frontend mission operations panel displays only API-backed mission properties, geodesic WGS84 area/perimeter estimates, the latest client-observed check time/result, and the dataset inventory limitations. These measurements are approximate spherical geodesic values, not operational tolerances. Saved mission export is a GeoJSON `Feature` with its existing Polygon and only `id`, `name`, `description`, and `createdAt` properties; coordinate order is longitude/latitude. Export is client-side and does not add a backend route.

## Hazard Service

| Method | Path | Purpose | Result |
|---|---|---|---|
| POST | `/api/v1/hazards` | Report and publish event | `201` |
| GET | `/api/v1/hazards` | Bounded recent active list | `200` |
| GET | `/api/v1/hazards/{hazardId}` | Retrieve report | `200` or `404` |
| POST | `/api/v1/hazards/{hazardId}/attachments/presign` | Optional short-lived upload authorization | `201` |
| GET | `/actuator/health` | Minimal safe health | `200`/`503` |

Illustrative request:

```json
{"type":"OBSTRUCTION","severity":"HIGH","location":{"type":"Point","coordinates":[77.595,12.975]},"description":"Demonstration report: temporary structure"}
```

Enum values and scoring meanings must be aligned before implementation. Optional upload validates MIME and byte limit; objects remain private, URL short-lived.

## Kafka contract (not HTTP)

Topic `hazard-events`; key is `hazardId` UUID string.

```json
{"eventId":"b9ce8a91-1e34-4a12-9a39-5834f1da920d","schemaVersion":1,"eventType":"HazardReported","occurredAt":"2026-01-15T12:30:00Z","hazardId":"2c2661f5-40ef-4805-a520-e58a0a2ae7bf","type":"OBSTRUCTION","severity":"HIGH","location":{"type":"Point","coordinates":[77.595,12.975]},"description":"Demonstration report"}
```

Validate envelope/payload, use stable identity, bounded retries, dedupe and explicit poison-event handling. Example is planned v1; do not silently change field meaning.

## Errors and validation

Planned statuses: `400` malformed/invalid input, `401` unauthenticated protected write, `403` forbidden, `404` unknown ID, `409` documented conflict, `413` oversized input, `415` unsupported media, optional `429`, generic safe `500`, `503` dependency unavailable. Problem response includes `type`, `title`, `status`, safe `detail`, `instance`, optional field violations. Never expose stack traces, SQL, secrets, or internal addresses. Auth/idempotency policy is undecided before implementation.
