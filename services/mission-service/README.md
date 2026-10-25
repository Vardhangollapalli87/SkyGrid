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

## Render

In the Mission Service Render service's Environment settings, set `SPRING_PROFILES_ACTIVE=production` and add `DATABASE_URL` with the provider-issued URI in the protected value field. Do not put it in `render.yaml`, source files, build commands, or logs. Render supplies `PORT`; the application already binds to `${PORT}` and falls back to `8081` for local runs.

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

Routes use a separate GeoJSON `route` property and LineString column; they never change the meaning of the existing Polygon `geometry`. `POST /api/v1/missions` creates a route mission. `PUT /api/v1/missions/{id}` replaces the mission's geometry representation and metadata, so callers must send the complete desired representation. Route positions are ordered longitude/latitude pairs, require at least two distinct consecutive positions, and are bounded to WGS84. Production must apply the additive `route` column statement in `db/production-spatial-schema.sql` before deploying this version.

Production Hibernate remains `validate`. Before production startup, manually run [`db/production-spatial-schema.sql`](db/production-spatial-schema.sql) against the Mission Service schema after PostGIS has already been enabled. The SQL is additive; review the target schema and backup policy before applying it.

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
