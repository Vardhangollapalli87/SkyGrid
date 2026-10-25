# Roadmap

The five-day MVP target remains a planning estimate. Scope stays within the fixed two-service architecture; future phases require owner authorization.

## Phase 0 — Product and cloud-first decisions (complete)

Project scope, API/data contracts, security constraints, cloud-first workflow, and provisional zero-cost provider comparison are documented. No production provider stack has been approved. See `docs/CLOUD_PROVIDER_COMPARISON.md`.

## Phase 1 — Mission Service CRUD (complete)

Spring Boot 3.5.16 / Java 25 Mission Service supports validated mission creation, bounded listing, retrieval by UUID, and Neon persistence. Local database URL properties remain private in the ignored local properties file. Production profile continues to validate the database schema.

## Phase 2 — PostGIS geospatial mission foundation (complete)

**Implemented:** optional GeoJSON Polygon on mission create; WGS84/SRID 4326 longitude-latitude validation; PostGIS polygon storage; restricted-zone polygon model; GiST zone index; PostGIS `ST_Intersects` check endpoint. Existing mission rows remain available with null geometry. No zones are seeded automatically, and no risk score or official restriction data is produced.

**Verification:** Neon PostgreSQL 18.6 reports PostGIS enabled. Read-only `ST_SRID`/`ST_Intersects` probe passed. The additive local schema update added the nullable geometry column and new zone table; existing mission rows remained queryable. Live repository and API integration tests use transaction rollback.

**Production schema:** run `services/mission-service/db/production-spatial-schema.sql` manually after PostGIS is enabled and before deploying with the production profile. The script does not create or enable the extension. Production Hibernate `validate` remains unchanged.

## Phase 3 — Hazard events (future)

Verify the selected managed Kafka service, TLS/auth, region and idle behavior. Implement the Hazard Service, versioned events, consumer deduplication/retries, and Mission Service hazard projection only when this phase is approved. Risk scoring/history must follow the fixed Mission Service ownership.

**Completed increment:** the separate Hazard Service now exposes stateless on-demand Open-Meteo forecast assessments for points, mission polygons, and bounded route samples. The frontend has an on-demand weather panel for saved missions. This does not implement hazard reports, persistence, Kafka events, mission risk scoring, or assessment history; those remain future work. See `services/hazard-service/README.md`.

## Phase 4 — Frontend (implemented)

**Implemented:** React/TypeScript/Vite/Leaflet workspace with responsive mission list/details, GeoJSON polygon drawing and draggable vertices, validation, mission creation, saved geometry rendering, backend restricted-zone checks, ordered route planning, GeoJSON exports, and on-demand weather assessments via Hazard Service. The API client has timeout/cancellation, bounded retry for safe GETs, and no POST retry. UI labels dataset and forecast limitations and never claims flight safety/clearance. See `docs/HANDOFF.md` for verification and remaining browser/API constraints.

## Phase 5 — Deployment and security (future)

Reconfirm hosting providers, Java 25 runtime, costs, network/TLS, CORS, secret handling, resource limits, and production schema setup. Introduce Docker only during deployment preparation. S3 evidence remains deferred.

## Phase 6 — Release (future)

Run end-to-end regressions, record measured deployment behavior, update documentation, and prepare the portfolio demo. Do not claim flight authorization, safety certification, or authoritative airspace data.
