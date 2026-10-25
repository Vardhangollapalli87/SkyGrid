# SkyGrid handoff

**Current state:** Mission Service CRUD/PostGIS geospatial foundation and the React/Vite mission workspace are implemented. Hazard Service, risk scoring, Kafka flow, authentication, and production deployment remain future work. The fixed two-service architecture and five-day MVP target remain in effect.

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

## Next work

1. Keep production schema validation; apply the checked-in additive schema SQL to the production schema before deploying this version.
2. Start Mission Service, run the frontend against its Vite `/api` proxy, and inspect desktop/mobile layouts in a browser. For deployment, set `VITE_API_BASE_URL` and configure Mission Service CORS for the actual frontend origin.
3. Before Phase 3, verify the selected Kafka service and hosting/network assumptions. Do not silently start Hazard Service or risk scoring without owner authorization.
4. Later provide a controlled way to load clearly labeled synthetic or approved restriction data; this phase intentionally does not seed database zones or add an import API.
