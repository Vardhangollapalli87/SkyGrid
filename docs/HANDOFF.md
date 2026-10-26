# SkyGrid handoff

## FAA import recovery (2026-10-26)

- Read-only Neon queries confirmed the FAA snapshot has 2,287 records and 2,287 unique nonempty OBJECTIDs. The database has 2,287 matching FAA rows/unique identities, 2,274 `VALID` geometries, 13 `INVALID_GEOMETRY` rows, and no duplicate identity groups. The unique source identity index exists.
- The active attempt is `IN_PROGRESS`: 18 batch markers cover 1,800 committed feature rows (1,796 valid, 4 invalid). All 18 legacy markers have `record_count IS NULL`; their counters report 1,800 skipped plus 4 rejected, double-counting those rejected rows. The importer cannot reconcile these old markers to source counts, and does not persist per-feature rejection reasons. For the four rows seen by this attempt, source-geometry validity was recomputed read-only with PostGIS: FAA IDs 2162, 2163, 2165, 2195 each have a ring self-intersection. Those reasons were derived, not stored.
- The current process listing check was blocked by Windows access denied, so current process state was not independently verified. No import or database mutation was run. A new attempt is generated on each run, so a complete FAA-only replay will create fresh markers, upsert by the existing unique source identity, and reconcile its own 2,287 records. Expected final status is `PARTIAL` because all 13 invalid geometries remain rejected; do not infer geographic completeness.
- Recovery command and preconditions are in `docs/AIRSPACE_DATA_IMPORT.md`. Do not use the older all-source command for this recovery. Regression tests cover FAA-only source selection, interrupted replay without duplicate inserts, rejected-row accounting, bounded transient retries, and refusal to finalize incomplete batch markers.

### Mission–route association and containment (2026-10-26)

- Saved route records remain in the existing `missions` table. Added nullable self-referencing `associated_mission_id`; multiple route-only rows may reference one area row. Existing Polygon areas and standalone LineString routes remain unchanged and are not auto-associated.
- Added route list/create/update, explicit standalone-route association/detachment, and full-LineString containment APIs. Containment uses PostGIS `ST_Covers`, treating boundary-touching segments as contained. Outside linework is returned for map highlighting. Result includes status, UTC check time, and mission/route geometry hashes. Geometry checks do not establish airspace safety or flight clearance.
- Frontend selected-area details show associated routes, create/attach actions, route selection/edit/export, and containment result. The result is hidden as soon as the selected route/area or either geometry changes; server response races are aborted/guarded.
- Production prerequisite: review and manually apply `services/mission-service/db/migrations/V2__mission_route_association.sql` after verifying PostGIS and `missions.route`; it adds nullable column, self-FK (`ON DELETE SET NULL`), route-only association check, and partial index. No database DDL or data changes were executed in this phase.
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

The Neon instance used for local development exists and was verified for this work. The previously researched zero-cost production candidates (Neon Free, Aiven Kafka Free, OCI A1, Vercel Hobby, with S3 deferred) remain provisional; region eligibility, pricing/quotas, Kafka connectivity, production host compatibility, and owner approval are unresolved. See `docs/CLOUD_PROVIDER_COMPARISON.md`. No resource identifiers or deployment URLs are recorded here.

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
- Vite proxies `/hazard-api/*` to local port 8082. For deployment, set `VITE_HAZARD_API_BASE_URL` to the public Hazard Service origin and configure its `CORS_ALLOWED_ORIGINS` for the frontend. Hazard Service requires outbound HTTPS/DNS to Open-Meteo and no credential for the configured forecast endpoint. See `services/hazard-service/README.md` and `infra/.env.example`.
- Provider HTTP behavior is covered by mocks; live weather-provider connectivity and deployed-host egress/CORS were not tested. Manual browser and small-screen interaction checks remain outstanding.

## Next work

### Real airspace snapshot milestone

The opt-in local snapshot importer now parses the retained snapshots and commits bounded batches of at most 100 records. A unique source identity, attempt/batch markers, upserts, and final stale-row cleanup make retries idempotent; incomplete/failed datasets are hidden from spatial reads until finalization. Netherlands circles and India ENR 5.1/5.2 source documents remain non-spatial. A previous live import opened a PostGIS session but the socket reset during the long FAA import, followed by a transient DNS lookup failure; no dataset completion, inventory query, or imported-data spatial query was verified. Importer unit tests pass, and PostGIS query coverage is opt-in. The additive production migration must be applied before production startup. Source completeness, licensing/reuse rights, and coverage remain unverified.

1. Keep production schema validation; apply the checked-in additive schema SQL to the production schema before deploying this version.
2. Start Mission Service, run the frontend against its Vite `/api` proxy, and inspect desktop/mobile layouts in a browser. For deployment, set `VITE_API_BASE_URL` and configure Mission Service CORS for the actual frontend origin.
3. Before enabling future hazard reporting or risk scoring, verify selected Kafka service and hosting/network assumptions. The implemented weather-only Hazard Service does not use Kafka.
4. Later provide a controlled way to load clearly labeled synthetic or approved restriction data; this phase intentionally does not seed database zones or add an import API.

### End-to-end deployment readiness check (2026-10-25)

- No deployment manifests or live public service/frontend URLs are present. OCI/A1 and Vercel Hobby are provisional provider candidates only; no provider is configured or deployed. The shared environment template now matches current service settings: Mission uses `SPRING_PROFILES_ACTIVE`, `DATABASE_URL`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, host `PORT`, and `CORS_ALLOWED_ORIGINS`; Hazard uses host `PORT`, configurable `WEATHER_*` values, `OPEN_METEO_BASE_URL`, and the same exact-origin CORS variable. Kafka variables are marked future-only. `VITE_API_BASE_URL` and `VITE_HAZARD_API_BASE_URL` are public frontend origins; blank values use Vite's local proxies.
- Mission CORS was absent from the runtime configuration. Added an exact-origin `CORS_ALLOWED_ORIGINS` filter (local default `http://localhost:5173`) with API-only GET/POST/PUT/OPTIONS methods and no credentials. Unit preflight tests pass for an allowed origin and reject an unlisted origin.
- Read-only Neon inspection confirmed `missions.geometry` exists and is nullable, `restricted_zones.geometry` exists and is non-null, but `missions.route` is absent. Mission startup with Hibernate `validate` connected to Neon and stopped with `Schema-validation: missing column [route] in table [missions]`. No automatic schema update or SQL migration was run. Do not start the service with local `ddl-auto=update` to hide this discrepancy. The checked-in additive schema script contains the route column prerequisite along with the restricted-zone and existing airspace-import tables/indexes; apply it only after reviewing the target database and planned deployment.
- The minimal recovery for that observed mismatch is the manually reviewed nullable `ALTER TABLE missions ADD COLUMN IF NOT EXISTS route geometry(LineString,4326)`. `services/mission-service/README.md` now provides PostGIS/schema preflight queries, pre/post row-count checks, and a safe manual rollout. The full SQL script includes unrelated importer/inventory DDL plus an `UPDATE` and `SET NOT NULL`, so it should not be blindly replayed on an established database. Only `route` was confirmed missing; remaining mapped-schema compatibility is unverified pending preflight and Hibernate validation after migration.
- Hazard Service starts locally, `/actuator/health` returns UP, local CORS preflight succeeds, and one real Open-Meteo Polygon forecast returned HTTP 200 using a saved mission geometry read-only from Neon. Mission-to-Hazard browser flow was not tested because Mission Service cannot start against the current schema. Route assessment against saved routes was likewise blocked by the missing `missions.route` column.
- At the time of this 2026-10-25 readiness check, the UI had no saved Polygon edit flow; route deletion used Mission PUT to clear route geometry, and the API still has no Mission DELETE endpoint. Browser-level layout, exports, loading/error states, and stale-response behavior were not exercised in that check.

### Geospatial validation and workspace UX corrections (2026-10-26)

- Leaflet click and drag handlers previously copied raw `LatLng.lng` into GeoJSON tuples. Leaflet world copies can report an unwrapped longitude such as `439.16` for the same displayed position as `79.16`; backend WGS84 validation correctly rejected it. Conversion now wraps only Leaflet-originated longitudes and returns numeric `[longitude, latitude]`; backend bounds checks remain strict. Display labels now say `Lat` and `Lon` explicitly.
- Added route-editor reducer state for cloning, edit history, reorder/remove, drag, and undo. Added Karimnagar-area regression fixtures for six-vertex Polygon and five-waypoint LineString; API tests cover Polygon/route serialization and reopen semantics. The backend regression confirms an unwrapped `439` longitude is still rejected if sent directly.
- Saved single-ring Polygon missions can reopen and update through the existing `PUT`. Polygons with interior rings remain visible but are not editable in the current single-ring editor, preventing loss of interior rings.
- Moved per-source inventory counts, attempt status/time, limitations, and layer toggles out of map overlays into the collapsible **Airspace Data & Coverage** section. The map shows compact inventory and viewport status. `IN_PROGRESS` and unverified coverage remain clearly identified; no coverage percentage or safety inference is introduced.
- Automated frontend tests, type-check, and production build passed; Mission Service tests/package passed. Manual browser checks remain: wrapped-world click/drag, multi-vertex Polygon edit/save/reopen, route drag/reorder/remove/undo/save/reopen, export download interaction, and desktop/mobile coverage drawer behavior. No database migration or airspace import was run.
