# SkyGrid handoff

## Render gateway deployment preparation (2026-10-26)

- Added a separate deployment-edge API Gateway; Mission Service and Hazard Service remain the only business services. The Gateway forwards existing mission, standalone route, airspace, and hazard API paths unchanged; it owns CORS, validates/creates correlation IDs, and returns sanitized upstream failure responses.
- Added Java 25 container build files for all three backend processes. Mission and Hazard are intended to be Render Private Services; Gateway is the only public backend. `VITE_API_BASE_URL` is the single public browser API origin, while blank local configuration retains the Vite proxies.
- This is preparation only. No Render/Vercel resources or services were created, no database connection or SQL was performed, and no deployment URL is known. See `docs/RENDER_DEPLOYMENT.md` for service settings and manual steps.
- Render Private Services are not free; they require owner plan/billing selection. Render Free web Gateway cold-starts and cannot be reached through private inbound traffic, though it can call paid private services. Spring Cloud 2025.0.3 supports Boot 3.5.x but reached end of open-source support on 2026-06-30. Both are unresolved deployment considerations.

## Initial MVP deployment decisions (2026-10-26)

- Owner accepts the unauthenticated Mission and Hazard APIs for this public portfolio demo, with the exposure risk understood. CORS is origin control for browsers, not API authentication.
- Owner deferred V2 for the initial MVP deployment. In the existing Neon schema, `missions.associated_mission_id` and its self-FK exist, but `ck_missions_route_association` and partial index `idx_missions_associated_mission` are absent. The application currently validates associations; the database does not enforce this check, and association lookups lack this index. No SQL was applied and the Neon database was not modified.
- Render is now the requested backend target for deployment preparation; no Render service, Vercel project, or public service URL is configured. SkyGrid is not confirmed deployed. Resource creation, plan selection, and deployment still require owner action.
- FAA remains `PARTIAL`: 2,274 spatial records, 13 rejected; coverage is unverified. This is not complete U.S. airspace coverage or flight clearance.

## FAA import recovery (2026-10-26)

- The FAA National Security Restrictions snapshot contains 2,287 records with 2,287 unique source IDs. Verified final database accounting is 2,287 rows: 2,274 valid spatial records, 13 rejected, 0 duplicates, and 0 failures. Import status is `PARTIAL`; coverage remains unverified.
- This is a limited national-security-restrictions dataset, not a complete U.S. airspace dataset. Do not present it as complete coverage, proof of safety, or flight clearance.
- Rejection reasons are not persisted in the feature table or exposed by the inventory API. Do not infer or invent reasons for the 13 records. The inventory endpoint maps stored status/count fields, but its live HTTP response has not been verified.
- The documented FAA-only command and a privacy-preserving Windows process check are in `docs/AIRSPACE_DATA_IMPORT.md`. Earlier interrupted-attempt details are superseded by the final accounting above. India and Netherlands notes below are unchanged.

### Mission–route association and containment (2026-10-26)

- Saved route records remain in the existing `missions` table. Added nullable self-referencing `associated_mission_id`; multiple route-only rows may reference one area row. Existing Polygon areas and standalone LineString routes remain unchanged and are not auto-associated.
- Added route list/create/update, explicit standalone-route association/detachment, and full-LineString containment APIs. Containment uses PostGIS `ST_Covers`, treating boundary-touching segments as contained. Outside linework is returned for map highlighting. Result includes status, UTC check time, and mission/route geometry hashes. Geometry checks do not establish airspace safety or flight clearance.
- Frontend selected-area details show associated routes, create/attach actions, route selection/edit/export, and containment result. The result is hidden as soon as the selected route/area or either geometry changes; server response races are aborted/guarded.
- V2 migration status: the existing Neon database already has the nullable association column and self-FK (`ON DELETE SET NULL`), but lacks the route-association check and partial index. The owner deferred V2 for the initial MVP deployment; no SQL or data changes were made. The missing database check leaves out-of-band writes without that invariant, and the missing index may slow association lookups as data grows.
- Verification: Mission Service `mvn --batch-mode -s target/maven-settings.xml test package` passed 73 tests (13 opt-in PostGIS tests skipped). Frontend `npm test` passed 31 tests; `npm run lint` and `npm run build` passed. Live PostGIS association/containment integration remains unverified until the migration is applied and the opt-in tests run; browser interaction remains manual.

**Current state:** Mission Service CRUD/PostGIS geospatial foundation and the React/Vite mission workspace are implemented. Hazard Service now provides stateless on-demand weather assessment from Open-Meteo for point, Polygon, and route geometries, with a frontend assessment panel. Hazard report persistence, risk scoring, Kafka flow, authentication, and production deployment remain future work. The fixed two-service architecture and five-day MVP target remain in effect.

## Verified Mission Service

- Spring Boot 3.5.16, Java 25, Maven.
- Existing local profile connects to Neon PostgreSQL 18.6. Local credentials stay in the ignored `application-local.properties`; that file was not displayed or modified, and the Maven JAR excludes it.
- PostGIS is enabled. A live check of `ST_SRID` and `ST_Intersects` at SRID 4326 passed.
- Existing mission CRUD and saved records remain in place. A nullable `geometry(Polygon,4326)` column was added additively. Existing missions retain their IDs/data and have null geometry until a future update path is implemented.
- `restricted_zones` stores UUID, name, Polygon geometry and a demonstration-data flag. A GiST index supports spatial filtering. The service does not seed zones automatically.
- Local/development Hibernate `update` creates the nullable column and new table; a local/development startup initializer creates the GiST index idempotently. It does not run `CREATE EXTENSION`.
- Production still uses Hibernate `validate`. Apply `services/mission-service/db/production-spatial-schema.sql` manually after enabling PostGIS and before production startup. The script is additive and contains no credentials.

## Implemented API

- Existing `POST /api/v1/missions`, `GET /api/v1/missions`, and `GET /api/v1/missions/{id}` remain compatible. Mission geometry is optional on create; responses include GeoJSON when present and omit it when absent.
- `GET /api/v1/missions/{id}/restricted-zone-check` runs a parameterized PostGIS `ST_Intersects` query and reports matching stored zones. It returns `400` if the mission has no geometry and `404` if the mission does not exist.
- Geometry is 2D GeoJSON Polygon, longitude first and latitude second, WGS84/SRID 4326. It must be closed, nonempty, valid, and within longitude/latitude ranges. Other types and invalid/self-intersecting rings return safe `400` responses.
- The backend geospatial phase did not add risk scoring, severity, an official restriction source, Hazard Service, Kafka, or S3 workflow. The frontend was implemented in a later authorized phase below.

## Tests and database safety

Unit/controller tests do not require a database. PostGIS tests are opt-in with `SKYGRID_POSTGIS_INTEGRATION=true` and use the existing local profile. Integration test writes run inside rollback transactions; read-only probes check PostGIS and current mission schema. Hibernate schema generation is disabled in these integration tests, so they require the additive local schema update to have been applied first.

Verification using the temporary Maven repository configuration:

- `mvn --batch-mode -s target/maven-settings.xml test`: **BUILD SUCCESS**, 37 tests, 0 failures/errors, 6 opt-in database tests skipped.
- With `SKYGRID_POSTGIS_INTEGRATION=true`, `mvn --batch-mode -s target/maven-settings.xml '-Dtest=PostgisAvailabilityIntegrationTest,PostgisSpatialIntegrationTest,MissionSpatialApiIntegrationTest' test`: **BUILD SUCCESS**, 6 tests, 0 failures/errors/skips, against the configured Neon database with PostGIS.
- `mvn --batch-mode -s target/maven-settings.xml package`: **BUILD SUCCESS**, executable Spring Boot JAR produced; bundled test phase 37 tests, 0 failures/errors, 6 opt-in database tests skipped.

## Cloud deployment status

The Neon instance used for local development exists and was verified. The current deployment preparation targets Render for the public Gateway and private backend services and Vercel for the frontend; no resources, URLs, or service settings are configured. Paid Render private plans require owner selection. The earlier OCI/Aiven zero-cost study remains historical, not the selected backend plan for this deployment. See `docs/RENDER_DEPLOYMENT.md`. No resource identifiers or deployment URLs are recorded here.

## Frontend workspace

- `frontend/` contains the React 19, TypeScript strict, Vite 5, Leaflet/React Leaflet workspace. Leave `VITE_API_BASE_URL` empty locally to use Vite's proxy to `http://localhost:8081`; configure the public API origin for deployment. `VITE_MAP_TILE_URL` is optional. Frontend variables must never contain secrets.
- The map defaults to a global view, includes attribution, map layer selection, scale, coordinate readout and zoom/extent controls. It uses public OpenStreetMap tiles by default; traffic is subject to provider policy/limits.
- Mission list/detail, polygon draw/edit, validation, save and restricted-zone check use the actual Mission API. Restricted-zone polygon geometries are not returned by the existing endpoint and are therefore not drawn. The endpoint also cannot distinguish an empty zone dataset from a complete no-match result. The UI states that dataset completeness is unknown.
- No browser automation or responsive viewport inspection was available in this implementation run. The local Mission Service was not listening on port 8081, so API end-to-end verification is pending. Vite proxies local `/api` requests to port 8081; production must set `VITE_API_BASE_URL` and configure Mission Service CORS for the deployed frontend origin.
- Frontend verification: `npm test` **passed**, 4 tests; `npm run build` **passed** with Vite production output. TypeScript strict checking is part of the build. `npm run lint` is a TypeScript project check.

### Mission operations panel

- Selected mission details now include saved state, UUID, Polygon validity/type, spherical geodesic WGS84 area/perimeter estimates, airspace check loading/result/error and client-observed timestamp, plus source inventory status and limitations.
- Saved valid single-ring mission polygons export as an RFC 7946 GeoJSON Feature with `id`, `name`, `description`, and `createdAt`. No route/waypoint data or unsupported readiness metrics are shown.
- Dataset coverage remains unverified. A no-intersection result is explicitly limited to returned data and is not flight clearance. Measurements are approximate geodesic display values.
- Verification for this change: `npm test` passed (8 tests), `npm run lint` passed, and `npm run build` passed. Browser layout/accessibility inspection remains manual; no Neon integration was required or run.

### Waypoint route planning

- Mission records now have an independent nullable PostGIS `geometry(LineString,4326)` route alongside the existing nullable Polygon. Existing Polygon records and payload semantics remain unchanged. Local/development Hibernate `update` adds the column; apply the additive statement in `services/mission-service/db/production-spatial-schema.sql` before production startup.
- `POST /api/v1/missions` accepts a `route` GeoJSON LineString. `PUT /api/v1/missions/{id}` replaces mission metadata and its optional geometry representation. Route positions retain their ordered longitude/latitude sequence and are validated for minimum count, bounds, and consecutive duplicates.
- The map supports numbered draggable waypoints, ordered list selection/reordering/deletion, bounded undo, approximate geodesic ground-track summary, saved route reopening/editing, confirmed route deletion, and RFC 7946 LineString export. Polygon planning/export remains separate and unchanged. Route-based airspace corridor checks are not implemented.
- Neon-backed integration was not run for this implementation; manual desktop/mobile browser interaction and production schema application/verification remain outstanding.

### Hazard Service weather assessment

- Added a separate Spring Boot 3.5.16 / Java 25 Hazard Service with `POST /api/v1/hazards/weather-assessment`. The service accepts Point, Polygon, or LineString geometry and optional bounded forecast horizon; it uses an interior representative point for polygons and deterministic geodesic samples along a complete route.
- Open-Meteo hourly forecast is retrieved on demand over HTTPS. The endpoint is stateless and makes no database, Kafka, S3, or other-service calls. Values include temperature, 10 m wind and gust, and precipitation; missing values are null and produce PARTIAL status. General thresholds and route/timeout limits are environment configurable. Upstream rate-limit, server, and network errors have bounded retries.
- The Mission workspace requests weather only when the user selects the action. It displays provider and retrieval metadata, UTC forecast horizon, route samples, ranges, configured thresholds, warning values/times, partial/error/stale states, and explicit limits. The app never turns weather into a risk score, readiness estimate, aircraft-specific limit, or clearance.
- Local development retains Vite proxies `/hazard-api/*` to port 8082. The deployment preparation now uses one public `VITE_API_BASE_URL` for the Gateway; Hazard remains private behind it and the Gateway owns browser CORS. Hazard Service requires outbound HTTPS/DNS to Open-Meteo and no credential for the configured forecast endpoint. See `docs/RENDER_DEPLOYMENT.md` and `services/hazard-service/README.md`.
- Provider HTTP behavior is covered by mocks; live weather-provider connectivity and deployed-host egress/CORS were not tested. Manual browser and small-screen interaction checks remain outstanding.

## Next work

### Real airspace snapshot milestone

The opt-in local snapshot importer parses the retained snapshots and commits bounded batches of at most 100 records. A unique source identity, attempt/batch markers, upserts, and final stale-row cleanup make retries idempotent; incomplete/failed datasets are hidden from spatial reads until finalization. Netherlands circles and India ENR 5.1/5.2 source documents remain non-spatial. The first live import attempt opened a PostGIS session but the socket reset during the FAA import, followed by a transient DNS lookup failure; that attempt did not complete or verify inventory/spatial results. The subsequent FAA recovery and verified database accounting are recorded at the top of this handoff. V2's check constraint and partial index remain deferred by owner; required production columns and the rest of the mapped schema still need verification against the deployment target. Source completeness, licensing/reuse rights, and geographic coverage remain unverified.

1. Keep production schema validation. Do not replay the broad additive schema script against the existing Neon database; perform a read-only schema preflight for the deployment target and defer V2 as decided for tonight.
2. Start Mission Service, run the frontend against its Vite `/api` proxy, and inspect desktop/mobile layouts in a browser. For deployment, set `VITE_API_BASE_URL` and configure Mission Service CORS for the actual frontend origin.
3. Before enabling future hazard reporting or risk scoring, verify selected Kafka service and hosting/network assumptions. The implemented weather-only Hazard Service does not use Kafka.
4. Later provide a controlled way to load clearly labeled synthetic or approved restriction data; this phase intentionally does not seed database zones or add an import API.

### End-to-end deployment readiness check (2026-10-25)

- Historical readiness note (2026-10-25): at that point no deployment manifests existed, OCI/A1 was a candidate, and separate frontend API variables were documented. Current Render/Vercel preparation and the single public `VITE_API_BASE_URL` are documented in `docs/RENDER_DEPLOYMENT.md`; no provider resources or live URLs are configured. See that document for current environment variables. Kafka variables remain future-only.
- Mission CORS was absent from the runtime configuration. Added an exact-origin `CORS_ALLOWED_ORIGINS` filter (local default `http://localhost:5173`) with API-only GET/POST/PUT/OPTIONS methods and no credentials. Unit preflight tests pass for an allowed origin and reject an unlisted origin.
- Read-only Neon inspection confirmed `missions.geometry` exists and is nullable, `restricted_zones.geometry` exists and is non-null, but `missions.route` is absent. Mission startup with Hibernate `validate` connected to Neon and stopped with `Schema-validation: missing column [route] in table [missions]`. No automatic schema update or SQL migration was run. Do not start the service with local `ddl-auto=update` to hide this discrepancy. The checked-in additive schema script contains the route column prerequisite along with the restricted-zone and existing airspace-import tables/indexes; apply it only after reviewing the target database and planned deployment.
- The minimal recovery for that observed mismatch is the manually reviewed nullable `ALTER TABLE missions ADD COLUMN IF NOT EXISTS route geometry(LineString,4326)`. `services/mission-service/README.md` now provides PostGIS/schema preflight queries, pre/post row-count checks, and a safe manual rollout. The full SQL script includes unrelated importer/inventory DDL plus an `UPDATE` and `SET NOT NULL`, so it should not be blindly replayed on an established database. Only `route` was confirmed missing; remaining mapped-schema compatibility is unverified pending preflight and Hibernate validation after migration.
- Hazard Service starts locally, `/actuator/health` returns UP, local CORS preflight succeeds, and one real Open-Meteo Polygon forecast returned HTTP 200 using a saved mission geometry read-only from Neon. Mission-to-Hazard browser flow was not tested because Mission Service cannot start against the current schema. Route assessment against saved routes was likewise blocked by the missing `missions.route` column.
- At the time of this 2026-10-25 readiness check, the UI had no saved Polygon edit flow; route deletion used Mission PUT to clear route geometry, and the API still has no Mission DELETE endpoint. Browser-level layout, exports, loading/error states, and stale-response behavior were not exercised in that check.

### Geospatial validation and workspace UX corrections (2026-10-26)

- Leaflet click and drag handlers previously copied raw `LatLng.lng` into GeoJSON tuples. Leaflet world copies can report an unwrapped longitude such as `439.16` for the same displayed position as `79.16`; backend WGS84 validation correctly rejected it. Conversion now wraps only Leaflet-originated longitudes and returns numeric `[longitude, latitude]`; backend bounds checks remain strict. Display labels now say `Lat` and `Lon` explicitly.
- Added route-editor reducer state for cloning, edit history, reorder/remove, drag, and undo. Added Karimnagar-area regression fixtures for six-vertex Polygon and five-waypoint LineString; API tests cover Polygon/route serialization and reopen semantics. The backend regression confirms an unwrapped `439` longitude is still rejected if sent directly.
- Saved single-ring Polygon missions can reopen and update through the existing `PUT`. Polygons with interior rings remain visible but are not editable in the current single-ring editor, preventing loss of interior rings.
- Moved per-source inventory counts, attempt status/time, limitations, and layer toggles out of map overlays into the collapsible **Airspace Data & Coverage** section. The map shows compact inventory and viewport status. `IN_PROGRESS`/`PARTIAL` states and unverified coverage remain clearly identified; no coverage percentage or safety inference is introduced.
- Automated frontend tests, type-check, and production build passed; Mission Service tests/package passed. Manual browser checks remain: wrapped-world click/drag, multi-vertex Polygon edit/save/reopen, route drag/reorder/remove/undo/save/reopen, export download interaction, and desktop/mobile coverage drawer behavior. No database migration or airspace import was run.
