# API contract

Implemented Mission Service routes and the Hazard Service weather-assessment route are identified below. Hazard reporting, assessment history, and map routes remain planned. JSON APIs use `/api/v1`, UUID IDs, UTC ISO-8601 times, GeoJSON longitude/latitude, validated input, and safe problem responses.

## Mission Service

| Method | Path | Purpose | Result |
|---|---|---|---|
| POST | `/api/v1/missions` | Create a mission with an optional Polygon area or LineString route | `201` |
| PUT | `/api/v1/missions/{missionId}` | Replace mission metadata and Polygon/LineString representation | `200` or `404` |
| GET | `/api/v1/missions` | Bounded paginated list | `200` |
| GET | `/api/v1/missions/{missionId}` | Retrieve mission, including geometry when present | `200` or `404` |
| GET | `/api/v1/missions/{missionId}/restricted-zone-check` | Check stored restricted-zone polygons with PostGIS `ST_Intersects` | `200`, `400` if geometry is absent, or `404` |
| GET | `/api/v1/missions/{missionId}/routes` | List routes associated with a mission area | `200` |
| POST | `/api/v1/missions/{missionId}/routes` | Create and associate a LineString route | `201` |
| PUT | `/api/v1/missions/{missionId}/routes/{routeId}` | Update a route without changing its area | `200` |
| DELETE | `/api/v1/missions/{missionId}/routes/{routeId}` | Clear route geometry and detach its record | `200` |
| GET | `/api/v1/missions/{missionId}/routes/{routeId}/containment` | Check the complete route with PostGIS | `200`; outcome is in the response status field |
| GET | `/api/v1/routes` | List standalone routes | `200` |
| PUT | `/api/v1/routes/{routeId}/association/{missionId}` | Associate an existing route with an area | `200` |
| DELETE | `/api/v1/routes/{routeId}/association` | Detach a route without changing its LineString | `200` |
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

Mission areas and route records remain separate rows in `missions`. Route rows may optionally reference an area through nullable `associated_mission_id`; multiple routes may reference one area. Existing rows are not auto-associated. The self-reference FK and check constraint require an associated row to contain a LineString and no Polygon geometry. Standalone routes retain a null association. Apply `services/mission-service/db/migrations/V2__mission_route_association.sql` manually before deploying this version.

Route containment returns `WITHIN_OR_ON_BOUNDARY`, `PARTLY_OUTSIDE`, `OUTSIDE`, `INVALID_GEOMETRY`, `MISSING_GEOMETRY`, or `UNAVAILABLE`. PostGIS `ST_Covers(area.geometry, route.route)` checks the complete LineString; boundary-touching segments count as contained. For outside results, `outsideGeometry` returns the portion outside the area as GeoJSON for display. `missionGeometryHash`, `routeGeometryHash`, and `checkedAt` identify the checked pair. The check is geometric only and never establishes clearance, legality, or safety.

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

Implemented weather assessment:

| Method | Path | Purpose | Result |
|---|---|---|---|
| POST | `/api/v1/hazards/weather-assessment` | Retrieve an on-demand Open-Meteo hourly forecast for a point, mission Polygon, or ordered route | `200`, `400` invalid input, `502` malformed/upstream response, or `503` provider unavailable after bounded retries |

Example route request:

```json
{"assessmentType":"ROUTE","geometry":{"type":"LineString","coordinates":[[77.59,12.97],[77.61,12.99]]},"forecastHours":24}
```

`assessmentType` is `POINT`, `POLYGON`, or `ROUTE`, and must match the GeoJSON type. `forecastHours` is optional (default 24) and bounded by the configured maximum (default 72). Requests are limited to 64 KiB. WGS84 coordinate order is `[longitude, latitude]`. Polygon assessments use one JTS interior point; route assessments validate 2–64 waypoints and sample the full geodesic route at configured spacing, with configured maximum sample count and route length. A point assessment accepts a GeoJSON Point. This endpoint does not require a saved mission ID and does not store request or response data.

The response includes assessment UUID, COMPLETE/PARTIAL status, provider name, retrieval instant, UTC timezone, forecast horizon and sampling strategy; per-location hourly values (temperature °C, wind/gust m/s, precipitation as mm accumulated over the preceding hour); aggregate ranges and route sample-mean ranges; configured general planning thresholds; threshold warning records with timestamps/locations; and limitations. Missing provider values are represented as `null`; they are not treated as zero. A response is PARTIAL if required fields or requested hourly forecast points are missing. A successful response means only that forecast data was obtained. No-warning output does not establish flight suitability, safety, legality, or clearance. It is not a current observation or aviation weather briefing. Open-Meteo fields follow its [Forecast API documentation](https://open-meteo.com/en/docs); precipitation is the preceding-hour sum in mm, and model/availability may change.

Provider HTTP calls use configurable connect/read timeouts (defaults 3 s/6 s) and up to three attempts with short bounded backoff for rate-limit, server, and network failures. No provider key is required for the configured forecast endpoint. Cross-origin access is limited by `CORS_ALLOWED_ORIGINS` (local default `http://localhost:5173`).

The separate weather endpoint does not implement the hazard-report CRUD, attachment presigning, persistence, or Kafka contracts listed below; those remain planned.

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
