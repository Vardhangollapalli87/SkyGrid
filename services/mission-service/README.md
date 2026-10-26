# Mission Service local database setup

The service reads a single `DATABASE_URL` environment variable in either standard PostgreSQL URI form:

```text
postgresql://USERNAME:PASSWORD@HOST/DATABASE?sslmode=require&channel_binding=require
# or
postgres://USERNAME:PASSWORD@HOST/DATABASE?sslmode=require&channel_binding=require
```

Render supplies the PostgreSQL URI through `DATABASE_URL`. Outside the `local` profile, the service converts it to the JDBC URL required by the PostgreSQL driver, decodes percent-encoded username/password components, passes credentials directly to Hikari, and preserves query parameters. The URI's `channel_binding` parameter is converted to pgJDBC's `channelBinding` property. Parse-failure diagnostics report only presence, character count, first code point as `U+...`, whether `://` occurs, detected scheme, and failed condition. They never print the URI or any component of it. The project pins pgJDBC 42.7.14 because earlier versions in this release line did not fully enforce `channelBinding=require`. Do not commit the real URL or include it in logs.

## Local PowerShell session

For local development, use Spring Boot's standard datasource properties instead of URL parsing. Create `src/main/resources/application-local.properties` (this path is ignored by Git) with the following keys, replacing every placeholder locally:

```properties
spring.datasource.url=jdbc:postgresql://HOST:5432/DATABASE?sslmode=require&channelBinding=require
spring.datasource.username=USERNAME
spring.datasource.password=PASSWORD
spring.jpa.hibernate.ddl-auto=update
```

Keep the file private and never commit it. The Maven executable JAR explicitly excludes this local file, while `spring-boot:run` still loads it from the development classpath. Run the service with the local profile:

```powershell
mvn -Dspring-boot.run.profiles=local spring-boot:run
```

I exercised `Read-Host -AsSecureString` followed by `PtrToStringBSTR` using a synthetic URI in Windows PowerShell. It round-tripped as a valid absolute PostgreSQL URI. The failed startup log proves only that its nonblank value had an unrecognized scheme; it does not prove the PowerShell conversion caused that result. Safe diagnostics now include length, first code point, delimiter presence, and detected scheme without exposing text. The local profile bypasses URI parsing entirely.

## Production container host (provider not selected)

Render is the requested deployment target for this preparation, but no Mission Service resource is configured or deployed. For the Gateway-only-public topology, deploy Mission as a Render Private Service in the same region as the Gateway and Hazard Service. Set `SPRING_PROFILES_ACTIVE=production`, protected `DATABASE_URL`, `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, and `PORT=10000`. The application binds to `0.0.0.0`; local default port remains `8081`. Its `/actuator/health` endpoint is available internally, while Render Private Services provide a TCP dashboard health check only. The Gateway handles browser CORS; it removes the `Origin` header before forwarding. Keep the real URI in Render secret settings; never put it in build files, source, or logs. See `../../docs/RENDER_DEPLOYMENT.md`.

The development profile uses Hibernate `update`; the production profile uses `validate`. The local profile connected to the existing Neon PostgreSQL 18.6 database, where PostGIS and a real SRID/intersection query were verified on 2026-10-25. No credentials are recorded here.

## Mission polygons and restricted-zone checks

`POST /api/v1/missions` keeps the existing `name` and optional `description` fields. Add an optional GeoJSON `geometry` to store a mission polygon:

```json
{
  "name": "Riverside survey",
  "description": "Optional",
  "geometry": {
    "type": "Polygon",
    "coordinates": [[[77.59,12.97],[77.60,12.97],[77.60,12.98],[77.59,12.98],[77.59,12.97]]]
  }
}
```

Only two-dimensional GeoJSON `Polygon` is accepted. Coordinates are `[longitude, latitude]` in WGS84/SRID 4326; rings must close and the polygon must be valid and nonempty. Longitude and latitude ranges are checked, and at most 10,000 positions are accepted. Existing missions without geometry remain valid; mission responses include geometry only when present.

Use `GET /api/v1/missions/{id}/restricted-zone-check` to evaluate the stored mission polygon against stored restricted-zone polygons with PostGIS `ST_Intersects`. The response contains `missionId`, `intersects`, `message`, `dataNotice`, and `zones` (`id`, `name`, `demonstration`). A mission without geometry returns `400`; an unknown mission returns `404`. Restricted-zone rows are not seeded automatically, and any synthetic zones must be labeled demonstration data. Results are not official aviation restrictions or flight clearance.

## Schema update safety

With local/development Hibernate `update`, startup adds nullable `geometry geometry(Polygon,4326)` and `route geometry(LineString,4326)` columns to `missions`, creates the new `restricted_zones` table, and creates its GiST index idempotently. Existing mission rows and identifiers are not dropped or rewritten; both geometries remain null for old records. The application does not enable PostGIS automatically.

Routes use a separate GeoJSON `route` property and LineString column; they never change the meaning of the existing Polygon `geometry`. `POST /api/v1/missions` creates a standalone route record. `PUT /api/v1/missions/{id}` retains its full-replacement behavior for the selected row. Route positions are ordered longitude/latitude pairs, require at least two distinct consecutive positions, and are bounded to WGS84. Optional area association is stored in nullable `missions.associated_mission_id`; old routes remain standalone.

### Mission-area route association and containment

The route APIs list/create/update routes under a mission area, associate an existing standalone route, and run a PostGIS containment check. Many route rows can reference one Polygon area through a self-FK. Association does not change the route coordinates or area Polygon. A database check constraint disallows associating a Polygon row or a route row without a LineString. Existing routes are not auto-linked.

For an existing database, manually review and apply [`db/migrations/V2__mission_route_association.sql`](db/migrations/V2__mission_route_association.sql) after confirming PostGIS and `missions.route` exist. It adds only a nullable UUID column, self-referencing FK with `ON DELETE SET NULL`, check constraint and partial index; existing rows retain null association. Do not replay the broader schema script to add this relationship. Safe rollout: record existing mission/route counts and take the normal backup; apply V2 manually; run the read-only checks below and confirm route and Polygon counts are unchanged; then deploy with Hibernate `validate`; finally exercise association/route persistence in a controlled test mission.

```sql
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name = 'missions'
  AND column_name = 'associated_mission_id';

SELECT conname, contype
FROM pg_constraint
WHERE conrelid = 'missions'::regclass
  AND conname IN ('fk_missions_associated_mission', 'ck_missions_route_association');

SELECT indexname FROM pg_indexes
WHERE schemaname = current_schema() AND tablename = 'missions'
  AND indexname = 'idx_missions_associated_mission';

SELECT count(*) AS mission_rows,
       count(*) FILTER (WHERE geometry IS NOT NULL) AS polygon_rows,
       count(*) FILTER (WHERE route IS NOT NULL) AS route_rows,
       count(*) FILTER (WHERE route IS NOT NULL AND associated_mission_id IS NULL) AS standalone_routes,
       count(*) FILTER (WHERE associated_mission_id IS NOT NULL) AS associated_routes
FROM missions;
```

Containment tests the complete LineString with PostGIS `ST_Covers`; boundary-touching segments count as contained. Outside portions are returned for map highlighting. Statuses distinguish within/on-boundary, partly outside, outside, invalid or missing geometry, and unavailable spatial query. This is geometric review only, not airspace clearance, legality, or safety. The UI invalidates results when either checked geometry changes.

Production Hibernate remains `validate`. Before production startup, manually run [`db/production-spatial-schema.sql`](db/production-spatial-schema.sql) against a new Mission Service schema after PostGIS has already been enabled. The script adds nullable mission Polygon, LineString, and association fields; creates `restricted_zones` with its GiST index; and adds the existing importer/inventory tables. It does not run `CREATE EXTENSION` or seed airspace rows. For an established database, review a narrowly scoped migration such as `db/migrations/V2__mission_route_association.sql`; do not replay the broad schema script to add this relationship. Review target schema and backup policy before applying DDL.

### Production route-column recovery (manual)

The 2026-10-25 read-only startup check connected to Neon and found `missions.geometry` present and nullable, `restricted_zones.geometry` present and non-null, but `missions.route` absent. Hibernate production validation therefore fails on `route`. That inspection did not establish that every other mapped column in every production table matches the current entities; run the preflight below against the intended schema and treat any mismatch separately.

PostGIS must already be installed/enabled in the target database. Check it and the relevant existing columns first:

```sql
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'postgis';

SELECT table_name, column_name, data_type, udt_name, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND ((table_name = 'missions' AND column_name IN ('geometry', 'route'))
       OR (table_name = 'restricted_zones' AND column_name = 'geometry'))
ORDER BY table_name, column_name;

SELECT count(*) AS mission_rows,
       count(*) FILTER (WHERE geometry IS NOT NULL) AS polygon_rows
FROM missions;
```

For the reported state, the minimal migration is only this nullable additive column. Review the target schema and take the normal database backup before applying:

```sql
ALTER TABLE missions
    ADD COLUMN IF NOT EXISTS route geometry(LineString, 4326);
```

This does not rewrite or remove mission IDs, names, timestamps, descriptions, or existing Polygon values. Existing rows receive `NULL` for `route`, preserving polygon meaning and behavior. Do not add a default, make the column non-null, or convert Polygon values into routes.

After applying, verify the column's PostGIS type/SRID/nullability and compare row totals with the preflight result:

```sql
SELECT f_geometry_column, type, srid, coord_dimension
FROM public.geometry_columns
WHERE f_table_schema = current_schema()
  AND f_table_name = 'missions'
  AND f_geometry_column = 'route';

SELECT column_name, is_nullable, udt_name
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name = 'missions'
  AND column_name = 'route';

SELECT count(*) AS mission_rows,
       count(*) FILTER (WHERE geometry IS NOT NULL) AS polygon_rows,
       count(*) FILTER (WHERE route IS NOT NULL) AS route_rows
FROM missions;
```

Expected route metadata is `LINESTRING`, SRID `4326`, nullable `YES`; existing mission and polygon counts should equal the recorded pre-migration values. Existing route count should be zero unless routes were already stored. Then start with the production profile and `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, confirm `/actuator/health`, and perform a controlled route create/read/update verification before considering production readiness. Route removal is represented by updating the mission with no route; there is no mission DELETE endpoint.

The mapped production tables must have these columns and compatible types: `missions` (`id` UUID, `name` varchar(120), nullable `description` varchar(1000), `created_at` timestamptz, nullable Polygon geometry, nullable LineString route); `restricted_zones` (`id` UUID, `name` varchar(120), non-null Polygon geometry, `demonstration` boolean); and `airspace_features` (UUID `id`, text identity fields, country/category, optional name/classification/geometry/source text/effective timestamp/last-seen attempt, JSONB source properties, geometry status, and non-null imported/last-attempt timestamps). The SQL script additionally supplies `airspace_dataset_imports` and `airspace_import_batches` for inventory/import endpoints; these are not entity tables checked by Hibernate but are required for those features. The previous live check confirmed only the named geometry columns and missing `route`; other schema compatibility remains unverified until preflight and successful Hibernate validation.

## Tests

The default Maven suite runs geometry/HTTP/unit tests without a database. Live Neon/PostGIS checks are opt-in and transactional where they insert fixtures:

```powershell
$env:SKYGRID_POSTGIS_INTEGRATION = "true"
try {
    mvn --batch-mode -s target/maven-settings.xml "-Dtest=PostgisAvailabilityIntegrationTest,PostgisSpatialIntegrationTest,MissionSpatialApiIntegrationTest" test
}
finally {
    Remove-Item Env:SKYGRID_POSTGIS_INTEGRATION -ErrorAction SilentlyContinue
}
```

These integration tests use the private local profile and require the additive schema update to have been applied first. Never copy local credentials into test sources or command arguments.
